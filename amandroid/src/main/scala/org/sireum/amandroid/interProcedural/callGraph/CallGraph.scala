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
import org.sireum.amandroid.interProcedural.InterProceduralGraph
import org.sireum.amandroid.interProcedural.InterProceduralNode
import org.sireum.amandroid.interProcedural.Context

/**
 * @author Fengguo Wei & Sankardas Roy
 */
class CallGraph[Node <: CGNode] extends InterProceduralGraph[Node]{
  private var succBranchMap : MMap[(Node, Option[Branch]), Node] = null
  private var predBranchMap : MMap[(Node, Option[Branch]), Node] = null
  val BRANCH_PROPERTY_KEY = ControlFlowGraph.BRANCH_PROPERTY_KEY
  
  private var entryN : CGNode = null

  private var exitN : CGNode = null
  
  def entryNode : Node = this.entryN.asInstanceOf[Node]
  
  def exitNode : Node = this.exitN.asInstanceOf[Node]
  
  /**
   * map from procedures to it's callee procedures
   */
  
  private val callMap : MMap[AmandroidProcedure, MSet[AmandroidProcedure]] = mmapEmpty
  
  def setCallMap(from : AmandroidProcedure, to : AmandroidProcedure) = this.callMap.getOrElseUpdate(from, msetEmpty).add(to)

  def getReachableProcedure(procs : Set[AmandroidProcedure]) : Set[AmandroidProcedure] = {
    caculateReachableProcedure(procs) ++ procs
  }
  
  private def caculateReachableProcedure(procs : Set[AmandroidProcedure]) : Set[AmandroidProcedure] = {
    if(procs.isEmpty) Set()
    else
      procs.map{
	      proc =>
	        val callees = callMap.getOrElse(proc, msetEmpty).toSet
	        callees ++ caculateReachableProcedure(callees)
	    }.reduce((s1, s2) => s1 ++ s2)
  }
  
  def reverse : CallGraph[Node] = {
    val result = new CallGraph[Node]
    for (n <- nodes) result.addNode(n)
    for (e <- edges) result.addEdge(e.target, e.source)
    result.entryN = this.exitNode
    result.exitN = this.entryNode
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
   
  def isCall(l : LocationDecl) : Boolean = l.isInstanceOf[JumpLocation] && l.asInstanceOf[JumpLocation].jump.isInstanceOf[CallJump]
   
  def collectCfgToBaseGraph[VirtualLabel](calleeProc : AmandroidProcedure, callerContext : Context, isFirst : Boolean = false) = {
    val calleeSig = calleeProc.getSignature
    val body = calleeProc.getProcedureBody
    val cfg = calleeProc.getCfg
    var nodes = isetEmpty[Node]
    cfg.nodes map{
      n =>
	      n match{
	        case vn : AlirVirtualNode[VirtualLabel] =>
	          vn.label.toString match{
	            case "Entry" => 
	              val entryNode = addCGEntryNode(callerContext.copy.setContext(calleeSig, calleeSig))
	              entryNode.setOwnerPST(body)
	              nodes += entryNode
	              if(isFirst) this.entryN = entryNode
	            case "Exit" => 
	              val exitNode = addCGExitNode(callerContext.copy.setContext(calleeSig, calleeSig))
	              exitNode.setOwnerPST(body)
	              nodes += exitNode
	              if(isFirst) this.exitN = exitNode
	            case a => throw new RuntimeException("unexpected virtual label: " + a)
	          }
	        case ln : AlirLocationUriNode=>
	          val l = body.location(ln.locIndex)
	          if(isCall(l)){
              val c = addCGCallNode(callerContext.copy.setContext(calleeSig, ln.locUri))
              c.setOwnerPST(body)
              c.asInstanceOf[CGLocNode].setLocIndex(ln.locIndex)
              nodes += c
              val r = addCGReturnNode(callerContext.copy.setContext(calleeSig, ln.locUri))
              r.setOwnerPST(body)
              r.asInstanceOf[CGLocNode].setLocIndex(ln.locIndex)
              nodes += r
              addEdge(c, r)
	          } else {
	            val node = addCGNormalNode(callerContext.copy.setContext(calleeSig, ln.locUri))
	            node.setOwnerPST(body)
	            node.asInstanceOf[CGLocNode].setLocIndex(ln.locIndex)
	            nodes += node
	          }
	        case a : AlirLocationNode => 
	          val node = addCGNormalNode(callerContext.copy.setContext(calleeSig, a.locIndex.toString))
	          node.setOwnerPST(body)
	          node.asInstanceOf[CGLocNode].setLocIndex(a.locIndex)
	          nodes += node
	      }
    }
    for (e <- cfg.edges) {
      val entryNode = getCGEntryNode(callerContext.copy.setContext(calleeSig, calleeSig))
      val exitNode = getCGExitNode(callerContext.copy.setContext(calleeSig, calleeSig))
      e.source match{
        case vns : AlirVirtualNode[VirtualLabel] =>
          e.target match{
            case vnt : AlirVirtualNode[VirtualLabel] =>
              addEdge(entryNode, exitNode)
            case lnt : AlirLocationUriNode =>
              val lt = body.location(lnt.locIndex)
		          if(isCall(lt)){
                val callNodeTarget = getCGCallNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
                addEdge(entryNode, callNodeTarget)
		          } else {
	              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
	              addEdge(entryNode, targetNode)
		          }
            case nt =>
              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, nt.toString))
              addEdge(entryNode, targetNode)
          }
        case lns : AlirLocationUriNode =>
          val ls = body.location(lns.locIndex)
          e.target match{
            case vnt : AlirVirtualNode[VirtualLabel] =>
              if(isCall(ls)){
                val returnNodeSource = getCGReturnNode(callerContext.copy.setContext(calleeSig, lns.locUri))
                addEdge(returnNodeSource, exitNode)
              } else {
                val sourceNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lns.locUri))
              	addEdge(sourceNode, exitNode)
              }
            case lnt : AlirLocationUriNode =>
              val lt = body.location(lnt.locIndex)
              if(isCall(ls)){
                val returnNodeSource = getCGReturnNode(callerContext.copy.setContext(calleeSig, lns.locUri))
                if(isCall(lt)){
	                val callNodeTarget = getCGCallNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
	                addEdge(returnNodeSource, callNodeTarget)
			          } else {
		              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
		              addEdge(returnNodeSource, targetNode)
			          }
              } else {
                val sourceNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lns.locUri))
                if(isCall(lt)){
	                val callNodeTarget = getCGCallNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
	                addEdge(sourceNode, callNodeTarget)
			          } else {
		              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
		              addEdge(sourceNode, targetNode)
			          }
              }
            case nt =>
              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, nt.toString))
              if(isCall(ls)){
                val returnNodeSource = getCGReturnNode(callerContext.copy.setContext(calleeSig, lns.locUri))
                addEdge(returnNodeSource, targetNode)
              } else {
                val sourceNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lns.locUri))
                addEdge(sourceNode, targetNode)
              }
          }
        case ns =>
          val sourceNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, ns.toString))
          e.target match{
            case vnt : AlirVirtualNode[VirtualLabel] =>
              addEdge(sourceNode, exitNode)
            case lnt : AlirLocationUriNode =>
              val lt = body.location(lnt.locIndex)
		          if(isCall(lt)){
                val callNodeTarget = getCGCallNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
                val returnNodeTarget = getCGReturnNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
                addEdge(sourceNode, callNodeTarget)
		          } else {
	              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, lnt.locUri))
	              addEdge(sourceNode, targetNode)
		          }
            case nt =>
              val targetNode = getCGNormalNode(callerContext.copy.setContext(calleeSig, nt.toString))
              addEdge(sourceNode, targetNode)
          }
      }
    }
    nodes
  }
  
  def extendGraph(calleeSig  : String, callerContext : Context) : Node = {
    val callNode = getCGCallNode(callerContext)
    val returnNode = getCGReturnNode(callerContext)
    val calleeContext = callerContext.copy
    calleeContext.setContext(calleeSig, calleeSig)
    val targetNode = getCGEntryNode(calleeContext)
    val retSrcNode = getCGExitNode(calleeContext)
    addEdge(callNode, targetNode)
    addEdge(retSrcNode, returnNode)
    targetNode
  }
  
  def addCGNormalNode(context : Context) : Node = {
    val node = newCGNormalNode(context).asInstanceOf[Node]
    val n =
      if (pool.contains(node)) pool(node)
      else {
        pl += (node -> node)
        node
      }
    graph.addVertex(n)
    n
  }
  
  def cgNormalNodeExists(context : Context) : Boolean = {
    graph.containsVertex(newCGNormalNode(context).asInstanceOf[Node])
  }
  
  def getCGNormalNode(context : Context) : Node =
    pool(newCGNormalNode(context))
  
  protected def newCGNormalNode(context : Context) =
    CGNormalNode(context)
    
  def addCGCallNode(context : Context) : Node = {
    val node = newCGCallNode(context).asInstanceOf[Node]
    val n =
      if (pool.contains(node)) pool(node)
      else {
        pl += (node -> node)
        node
      }
    graph.addVertex(n)
    n
  }
  
  def cgCallNodeExists(context : Context) : Boolean = {
    graph.containsVertex(newCGCallNode(context).asInstanceOf[Node])
  }
  
  def getCGCallNode(context : Context) : Node =
    pool(newCGCallNode(context))
  
  protected def newCGCallNode(context : Context) =
    CGCallNode(context)
    
  def addCGReturnNode(context : Context) : Node = {
    val node = newCGReturnNode(context).asInstanceOf[Node]
    val n =
      if (pool.contains(node)) pool(node)
      else {
        pl += (node -> node)
        node
      }
    graph.addVertex(n)
    n
  }
  
  def cgReturnNodeExists(context : Context) : Boolean = {
    graph.containsVertex(newCGReturnNode(context).asInstanceOf[Node])
  }
  
  def getCGReturnNode(context : Context) : Node =
    pool(newCGReturnNode(context))
  
  protected def newCGReturnNode(context : Context) =
    CGReturnNode(context)
  
    
  def addCGEntryNode(context : Context) : Node = {
    val node = newCGEntryNode(context).asInstanceOf[Node]
    val n =
      if (pool.contains(node)) pool(node)
      else {
        pl += (node -> node)
        node
      }
    graph.addVertex(n)
    n
  }
  
  def cgEntryNodeExists(context : Context) : Boolean = {
    graph.containsVertex(newCGEntryNode(context).asInstanceOf[Node])
  }
  
  def getCGEntryNode(context : Context) : Node =
    pool(newCGEntryNode(context))
  
  protected def newCGEntryNode(context : Context) =
    CGEntryNode(context)
    
  def addCGExitNode(context : Context) : Node = {
    val node = newCGExitNode(context).asInstanceOf[Node]
    val n =
      if (pool.contains(node)) pool(node)
      else {
        pl += (node -> node)
        node
      }
    graph.addVertex(n)
    n
  }
  
  def cgExitNodeExists(context : Context) : Boolean = {
    graph.containsVertex(newCGExitNode(context).asInstanceOf[Node])
  }
  
  def getCGExitNode(context : Context) : Node =
    pool(newCGExitNode(context))
  
  protected def newCGExitNode(context : Context) =
    CGExitNode(context)
  
}

sealed abstract class CGNode(context : Context) extends InterProceduralNode(context){
  protected var ownerPST : ProcedureSymbolTable = null
  def setOwnerPST(pst : ProcedureSymbolTable)  = this.ownerPST = pst
  def getOwnerPST = this.ownerPST
}

abstract class CGVirtualNode(context : Context) extends CGNode(context) {
  def getVirtualLabel : String
}

final case class CGEntryNode(context : Context) extends CGVirtualNode(context){
  def getVirtualLabel : String = "Entry"
  override def toString : String = "Entry@" + context
}

final case class CGExitNode(context : Context) extends CGVirtualNode(context){
  def getVirtualLabel : String = "Exit"
  override def toString : String = "Exit@" + context
}

abstract class CGLocNode(context : Context) extends CGNode(context) {
  def getLocUri : String = context.getLocUri
  protected val LOC_INDEX = "LocIndex"
  def setLocIndex(i : Int) = setProperty(LOC_INDEX, i)
  def getLocIndex : Int = getPropertyOrElse[Int](LOC_INDEX, throw new RuntimeException("did not have loc index"))
}

abstract class CGInvokeNode(context : Context) extends CGLocNode(context) {
  def getInvokeLabel : String
}

final case class CGCallNode(context : Context) extends CGInvokeNode(context){
  def getInvokeLabel : String = "Call"
  override def toString : String = getInvokeLabel + "@" + context
}

final case class CGReturnNode(context : Context) extends CGInvokeNode(context){
  def getInvokeLabel : String = "Return"
  override def toString : String = getInvokeLabel + "@" + context
}

final case class CGNormalNode(context : Context) extends CGLocNode(context){
  override def toString : String = context.toString
}
