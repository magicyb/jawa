package org.sireum.amandroid.interProcedural.callGraph

import org.sireum.alir._
import org.sireum.pilar.symbol.ProcedureSymbolTable
import org.sireum.util._
import org.sireum.pilar.ast._
import org.jgrapht.ext.VertexNameProvider
import java.io._
import org.jgrapht.ext.DOTExporter
import dk.brics.automaton._
import org.sireum.amandroid.intraProcedural.compressedControlFlowGraph.AlirIntraProceduralGraphExtra
import org.sireum.amandroid.AmandroidProcedure

case class ReachableProcedure(callerProcedure : AmandroidProcedure, calleeProcedure : AmandroidProcedure, locUri: Option[org.sireum.util.ResourceUri], locIndex: Int, params : List[String])


class CallGraph[VirtualLabel] extends SuperControlFlowGraph[VirtualLabel]
		with AlirEdgeAccesses[AlirIntraProceduralNode]
		with AlirIntraProceduralGraphExtra[AlirIntraProceduralNode, VirtualLabel]{
    type Node = AlirIntraProceduralNode
    private var succBranchMap : MMap[(Node, Option[Branch]), Node] = null
    private var predBranchMap : MMap[(Node, Option[Branch]), Node] = null
    val BRANCH_PROPERTY_KEY = ControlFlowGraph.BRANCH_PROPERTY_KEY
    var entryNode : Node = null
    var exitNode : Node = null
    protected val pl : MMap[AlirIntraProceduralNode, Node] = mmapEmpty
    protected def pool : MMap[AlirIntraProceduralNode, Node] = pl
    
	  /**
		 * Get all reachable procedures of given procedure. (Do not include transitive call)
		 * @param procedureUris Initial procedure resource uri
		 * @return Set of reachable procedure resource uris from initial procedure
		 */
		def getReachableProcedures(procedureUri : AmandroidProcedure) : Set[ReachableProcedure] = Set()
		/**
		 * Get all reachable procedures of given procedure set. (Do not include transitive call)
		 * @param procedureUris Initial procedure resource uri set
		 * @return Set of reachable procedure resource uris from initial set
		 */
		def getReachableProcedures(procedureUris : Set[AmandroidProcedure]) : Set[ReachableProcedure] = Set()

    def reverse : CallGraph[VirtualLabel] = {
      val result = new CallGraph[VirtualLabel]
      for (n <- nodes) result.addNode(n)
      for (e <- edges) result.addEdge(e.target, e.source)
      result.entryNode = exitNode
      result.exitNode = entryNode
      result
    }
    
  private def putBranchOnEdge(trans : Int, branch : Int, e : Edge) = {
    e(BRANCH_PROPERTY_KEY) = (trans, branch)
  }

  private def getBranch(pst : ProcedureSymbolTable, e : Edge) : Option[Branch] = {
    if (e ? BRANCH_PROPERTY_KEY) {
      val p : (Int, Int) = e(BRANCH_PROPERTY_KEY)
      var j =
        PilarAstUtil.getJumps(pst.location(
          e.source.asInstanceOf[AlirLocationNode].locIndex))(first2(p)).get
      val i = second2(p)

      if (j.isInstanceOf[CallJump])
        j = j.asInstanceOf[CallJump].jump.get

      (j : @unchecked) match {
        case gj : GotoJump   => Some(gj)
        case rj : ReturnJump => Some(rj)
        case ifj : IfJump =>
          if (i == 0) ifj.ifElse
          else Some(ifj.ifThens(i - 1))
        case sj : SwitchJump =>
          if (i == 0) sj.defaultCase
          else (Some(sj.cases(i - 1)))
      }
    } else None
  }

	def useBranch[T](pst : ProcedureSymbolTable)(f : => T) : T = {
	  succBranchMap = mmapEmpty
	  predBranchMap = mmapEmpty
	  for (node <- this.nodes) {
	    for (succEdge <- successorEdges(node)) {
	      val b = getBranch(pst, succEdge)
	      val s = edgeSource(succEdge)
	      val t = edgeTarget(succEdge)
	      succBranchMap((node, b)) = t
	      predBranchMap((t, b)) = s
	    }
	  }
	  val result = f
	  succBranchMap = null
	  predBranchMap = null
	  result
	}
    
    
    def successor(node : Node, branch : Option[Branch]) : Node = {
      assert(succBranchMap != null,
        "The successor method needs useBranch as enclosing context")
      succBranchMap((node, branch))
    }

    def predecessor(node : Node, branch : Option[Branch]) : Node = {
      assert(predBranchMap != null,
        "The successor method needs useBranch as enclosing context")
      predBranchMap((node, branch))
    }

    override def toString = {
      val sb = new StringBuilder("system CFG\n")

      for (n <- nodes)
        for (m <- successors(n)) {
          for (e <- getEdges(n, m)) {
            val branch = if (e ? BRANCH_PROPERTY_KEY)
              e(BRANCH_PROPERTY_KEY).toString
            else ""
              sb.append("%s -> %s %s\n".format(n, m, branch))
          }
        }

      sb.append("\n")

      sb.toString
    }
   
  override protected val vlabelProvider = new VertexNameProvider[Node]() {
    
	def filterLabel(uri : String) = {
	  if(uri.startsWith("pilar:/procedure/default/%5B%7C"))
		  uri.replace("pilar:/procedure/default/%5B%7C", "").filter(_.isUnicodeIdentifierPart)  // filters out the special characters like '/', '.', '%', etc.  
	  else uri.filter(_.isUnicodeIdentifierPart)  // filters out the special characters like '/', '.', '%', etc.  
	}
    
	def getVertexName(v : Node) : String = {
	  v match {
	    case n1: AlirLocationNode  => filterLabel(n1.toString())       
	    case n2: AlirVirtualNode[VirtualLabel]   => filterLabel(n2.toString())
	  }
	}
  }
    
  override def toDot(w : Writer) = {
    val de = new DOTExporter[Node, Edge](vlabelProvider, vlabelProvider, null)
    de.export(w, graph)
  }
  
  /**
   * (We ASSUME that predecessors ???? and successors of n are within the same procedure as of n)
   * So, this algorithm is only for an internal node of a procedure NOT for a procedure's Entry node or Exit node
   * The algorithm is obvious from the following code 
   */
  def compressByDelNode (n : Node) = {
    val preds = predecessors(n)
    val succs = successors(n)
    for(pred <- preds)
      deleteEdge(pred, n)
    for(succ <- succs)
      deleteEdge(n, succ) 
    for(pred <- preds){
      for(succ <- succs){           
        if (!hasEdge(pred,succ))
           addEdge(pred, succ)
      }
    }
//    println("deleteNode = " + n)
    deleteNode(n)
  }
   
   // read the sCfg and build a corresponding DFA/NFA
  
   def buildAutomata() : Automaton = {
    val automata = new Automaton()
    // build a map between sCfg-nodes-set and automata-nodes-set
    val nodeMap:MMap[Node, State] = mmapEmpty
    
    nodes.foreach(
        gNode => {
          val state = new State()
          state.setAccept(true)  // making each state in the automata an accept state
          nodeMap(gNode) = state
        }
    )
    // build a map between Entry-nodes-set (in sCfg) to English characters (assuming Entry-nodes-set is small); note that each call corresponds to an edge to Entry node of callee proc
    val calleeMap:MMap[Node, Char] = mmapEmpty
    var calleeIndex = 0
    nodes.foreach(
        gNode => {   // ******* below check the hard-coded path for testing ***********
          if(!gNode.toString().contains("pilar:/procedure/default/%5B%7Cde::mobinauten") && gNode.toString().endsWith(".Entry"))
          {
            calleeMap(gNode) = ('A' + calleeIndex).toChar
            println("in calleeMap: node " + gNode.toString() + "  has label = " + calleeMap(gNode))
            calleeIndex = calleeIndex + 1
          }
        }
    )
    // build the automata from the sCfg
    
    nodes.foreach(
        gNode => {
          val automataNode = nodeMap(gNode)   // automataNode = automata state
          val gSuccs = successors(gNode)
          var label: Char = 'x'  // default label of automata transition
          gSuccs.foreach(
              gSucc => {
                val automataSucc = nodeMap(gSucc)
                if(calleeMap.contains(gSucc))
                  label = calleeMap(gSucc)
                val tr = new Transition(label, automataSucc)
                automataNode.addTransition(tr)
              }
          )
        }
    )
    // get start node S from sCfg by searching with relevant pUri and then get corresponding automata state
    nodes.foreach(
        gNode => { // ******* below check the hard-coded path for testing ***********
		   if(gNode.toString().contains("pilar:/procedure/default/%5B%7Cde::mobinauten::smsspy::EmergencyTask.onLocationChanged%7C%5D/1/23/51eae215") && gNode.toString().endsWith(".Entry")) 
		   {
			   // val gStartNode = getVirtualNode("pilar:/procedure/default/%5B%7Cde::mobinauten::smsspy::EmergencyTask.onLocationChanged%7C%5D/1/23/51eae215.Entry".asInstanceOf[VirtualLabel]
			   val startState = nodeMap(gNode)
			   automata.setInitialState(startState)
			   println(automata.toDot())
		   }
		}       
    )
   automata
  }
   
  def collectionCfgToBaseGraph(pUri : ResourceUri, cfg : ControlFlowGraph[VirtualLabel]) = {
    
    val newNodes = cfg.nodes map{
      n =>
	      n match{
	        case vn : AlirVirtualNode[VirtualLabel] =>
	          addVirtualNode((pUri + "." + vn.label).asInstanceOf[VirtualLabel]) 
	        case n =>
	          pool(n) = n
	          addNode(n)
	      }
    }
    for (e <- cfg.edges) {
      val entryNode = getVirtualNode((pUri + "." + "Entry").asInstanceOf[VirtualLabel])
      val exitNode = getVirtualNode((pUri + "." + "Exit").asInstanceOf[VirtualLabel])
      if(e.source.isInstanceOf[AlirVirtualNode[VirtualLabel]]) addEdge(entryNode, e.target)
      else if(e.target.isInstanceOf[AlirVirtualNode[VirtualLabel]]) addEdge(e.source, exitNode)
      else addEdge(e)
    }
  }
  
  def extendGraph(callee : ResourceUri, pUri : ResourceUri, lUri : ResourceUri, lIndex : Int) = {
    val srcNode = getNode(Some(lUri), lIndex)
    val targetNode = getVirtualNode((callee + "." + "Entry").asInstanceOf[VirtualLabel])
    val retSrcNode = getVirtualNode((callee + "." + "Exit").asInstanceOf[VirtualLabel])
    successors(srcNode).foreach{
      retTargetnode =>
        deleteEdge(srcNode, retTargetnode)
        addEdge(retSrcNode, retTargetnode)
    }
    addEdge(srcNode, targetNode)
  }
  
}
