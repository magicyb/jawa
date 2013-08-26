package org.sireum.amandroid.android.interProcedural.objectFlowAnalysis

import org.sireum.alir.AlirEdge
import org.sireum.alir.ControlFlowGraph
import org.sireum.util._
import org.sireum.pilar.symbol.ProcedureSymbolTable
import org.sireum.pilar.ast._
import org.sireum.alir.ReachingDefinitionAnalysis
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import scala.collection.JavaConversions._
import org.sireum.amandroid.util.SignatureParser
import org.sireum.amandroid.interProcedural.objectFlowAnalysis._
import org.sireum.amandroid.android.appInfo.PrepareApp
import org.sireum.amandroid.interProcedural.Context
import org.sireum.amandroid._
import org.sireum.amandroid.android.cache.AndroidCacheFile
import org.sireum.amandroid.interProcedural.callGraph.CallGraph
import org.sireum.amandroid.interProcedural.callGraph.SuperControlFlowGraph
import org.sireum.amandroid.intraProcedural.compressedControlFlowGraph.CompressedControlFlowGraph



class AndroidOfgAndScfgBuilder[Node <: OfaNode, ValueSet <: AndroidValueSet, VirtualLabel](val fac: () => ValueSet) {

  type Edge = AlirEdge[Node]
  
  var appInfo : PrepareApp = null
  var pstMap : Map[String, ProcedureSymbolTable] = Map()
  var processed : Map[(String, Context), PointProc] = Map()
  var cfgs : MMap[String, ControlFlowGraph[String]] = null
  var rdas : MMap[String, ReachingDefinitionAnalysis.Result] = null
  //a map from return node to its possible updated value set
  var modelOperationValueSetMap : Map[Node, ValueSet] = Map()
  
  def apply(psts : Seq[ProcedureSymbolTable],
            cfgs : MMap[String, ControlFlowGraph[String]],
            rdas : MMap[String, ReachingDefinitionAnalysis.Result],
            appInfoOpt : Option[PrepareApp]) 
            = build(psts, cfgs, rdas, appInfoOpt)

  def build(psts : Seq[ProcedureSymbolTable],
            cfgs : MMap[String, ControlFlowGraph[String]],
            rdas : MMap[String, ReachingDefinitionAnalysis.Result],
            appInfoOpt : Option[PrepareApp])
   : (AndroidObjectFlowGraph[Node, ValueSet], SuperControlFlowGraph[String]) = {
    this.cfgs = cfgs
    this.rdas = rdas
    this.pstMap =
      psts.map{
	      pst =>
	        (pst.procedureUri, pst)
    	}.toMap
    val ofg = new AndroidObjectFlowGraph[Node, ValueSet](fac)
    val cg = new CallGraph[String]
    appInfoOpt match{
      case Some(appInfo) =>
        this.appInfo = appInfo
        ofaWithIcc(ofg, cg)
      case None =>
        ofa(ofg, cg)
    }
    val result = (ofg, cg)
    ofg.nodes.foreach(
      node => {
        val name = node.toString()
        node match{
          case ofb : OfaFieldBaseNode =>
            val valueSet = node.getProperty(result._1.VALUE_SET_EXIT).asInstanceOf[ValueSet]
	        	println("node:" + name + "\n" + valueSet)
          case _ =>
            val valueSet = node.getProperty(result._1.VALUE_SET).asInstanceOf[ValueSet]
	        	println("node:" + name + "\n" + valueSet)
        }
      }
    )
    println("processed--->" + processed.size)
//    processed.foreach{
//      item =>
//        println("item--->" + item)
//    }
    val f = new File(System.getProperty("user.home") + "/Desktop/ofg.dot")
    val o = new FileOutputStream(f)
    val w = new OutputStreamWriter(o)
    result._1.toDot(w)
    val f1 = new File(System.getProperty("user.home") + "/Desktop/sCfg.dot")
    val o1 = new FileOutputStream(f1)
    val w1 = new OutputStreamWriter(o1)
    result._2.toDot(w1)
    
    // this is only for a test
//    val f2 = new File(System.getProperty("user.home") + "/Desktop/fieldRepo")
//    val o2 = new FileOutputStream(f2)
//    val w2 = new OutputStreamWriter(o2)
//    val fRepo = result._1.iFieldDefRepo
//    w2.write(fRepo.toString)
    // the test ends
    result
  }
  
  def ofa(ofg : AndroidObjectFlowGraph[Node, ValueSet],
          sCfg : SuperControlFlowGraph[String]) = {
    pstMap.keys.foreach{
      uri =>
        if(uri.contains(".main%7C%5D") || uri.contains(".dummyMain%7C%5D")){
	        val cfg = cfgs(uri)
			    val rda = rdas(uri)
			    val pst = pstMap(uri)
			    doOFA(pst, cfg, rda, ofg, sCfg)
        }
    }
  }
  
  def ofaWithIcc(ofg : AndroidObjectFlowGraph[Node, ValueSet],
          sCfg : SuperControlFlowGraph[String]) = {
    ofg.setIntentFdb(appInfo.getIntentDB)
    ofg.setEntryPoints(appInfo.getEntryPoints)
    appInfo.getDummyMainMap.values.foreach{
      dummy =>
        val cfg = cfgs(dummy.getSignature)
		    val rda = rdas(dummy.getSignature)
		    val pst = dummy.getProcedureBody
		    doOFA(pst, cfg, rda, ofg, sCfg)
    }
    overallFix(ofg, sCfg)
  }
  
  def doOFA(pst : ProcedureSymbolTable,
            cfg : ControlFlowGraph[String],
            rda : ReachingDefinitionAnalysis.Result,
            ofg : AndroidObjectFlowGraph[Node, ValueSet],
            sCfg : SuperControlFlowGraph[String]) : Unit = {
    val points = new PointsCollector().points(pst)
    val context : Context = new Context(ofg.K_CONTEXT)
    ofg.points ++= points
    setProcessed(points, pst.procedureUri, context.copy)
    ofg.constructGraph(pst.procedureUri, points, context.copy, cfg, rda)
    sCfg.collectionCfgToBaseGraph(pst.procedureUri, cfg)
    fix(ofg, sCfg)
  }
  
  def overallFix(ofg : AndroidObjectFlowGraph[Node, ValueSet],
		  					 sCfg : SuperControlFlowGraph[String]) : Unit = {
    while(checkAndDoIccOperation(ofg, sCfg)){
    	fix(ofg, sCfg)
    }
  }
  
  private def getValueSet(node : Node, ofg : AndroidObjectFlowGraph[Node, ValueSet], isSucc : Boolean) : ValueSet = {
    node match{
      case ofbl : OfaFieldBaseNodeL =>
        if(isSucc)
          ofbl.getProperty[ValueSet](ofg.VALUE_SET_ENTRY)
        else
        	ofbl.getProperty[ValueSet](ofg.VALUE_SET_EXIT)
      case ofbr : OfaFieldBaseNodeR =>
        if(isSucc)
          ofbr.getProperty[ValueSet](ofg.VALUE_SET_ENTRY)
        else
        	ofbr.getProperty[ValueSet](ofg.VALUE_SET_EXIT)
      case _ => 
        node.getProperty[ValueSet](ofg.VALUE_SET)
    }
  }
  
  def fix(ofg : AndroidObjectFlowGraph[Node, ValueSet],
		  		sCfg : SuperControlFlowGraph[String]) : Unit = {
    while (!ofg.worklist.isEmpty) {
      val n = ofg.worklist.remove(0)
      val tmpworklist = mlistEmpty ++ ofg.worklist
      if(n.isInstanceOf[OfaInvokeNode] && n.asInstanceOf[OfaInvokeNode].typ == "static"){
        val pi = n.asInstanceOf[OfaInvokeNode].getPI
        val callerContext = n.getContext
        val callee = ofg.getDirectCallee(pi)
        extendGraphWithConstructGraph(callee.getSignature, pi, callerContext.copy, ofg, sCfg)
      }
      val vsN = getValueSet(n, ofg, false)
      ofg.successors(n).foreach(
        succ => {
          val vsSucc = getValueSet(succ, ofg, true)
          val d = vsN.getDiff(vsSucc).asInstanceOf[ValueSet]
          if(!d.isEmpty){
            vsSucc.merge(d)
            ofg.worklist += succ
            checkAndDoFieldAndGlobalOperation(succ, d, ofg)
            checkAndDoModelOperation(succ, ofg)
            val piOpt = ofg.recvInverse(succ)
            piOpt match {
              case Some(pi) =>
                val callerContext : Context = succ.getContext
                val calleeSet : MSet[AmandroidProcedure] = msetEmpty
                if(pi.typ.equals("direct") || pi.typ.equals("super")){
                  calleeSet += ofg.getDirectCallee(pi)
                } else {
                  calleeSet ++= ofg.getCalleeSet(d, pi)
                }
                calleeSet.foreach(
                  callee => {
                    extendGraphWithConstructGraph(callee.getSignature, pi, callerContext.copy, ofg, sCfg)
                  }  
                )
              case None =>
            }
          }
        }  
      )
      //do special operation
    }
  }
  
  
  def checkAndDoFieldAndGlobalOperation(succNode : Node, d : ValueSet, ofg : ObjectFlowGraph[Node, ValueSet]) = {
    //check whether it's a global variable node, if yes, then populate globalDefRepo
     //check whether it's a base node of field access, if yes, then populate/use iFieldDefRepo.
    succNode match {
      case ogvn : OfaGlobalVarNode =>
        ofg.populateGlobalDefRepo(d, ogvn)
      case ofbnl : OfaFieldBaseNodeL =>
        ofg.updateBaseNodeValueSet(ofbnl)
      case ofbnr : OfaFieldBaseNodeR =>
        ofg.updateFieldValueSet(ofbnr.fieldNode)
        ofg.worklist += ofbnr.fieldNode.asInstanceOf[Node]
      case ofn : OfaFieldNode =>		//this means field appear on LHS and n is on RHS (vice versa)
        ofg.populateFieldRepo(ofn)
        ofg.worklist += ofn.baseNode.asInstanceOf[Node]
      case _ =>
    }
  }
  
  def checkAndDoModelOperation(succNode : Node, ofg : AndroidObjectFlowGraph[Node, ValueSet]) = {
    ofg.getInvokePointNodeInModelOperationTrackerFromCallComponent(succNode) match{
      case Some(ipN) =>
        doSpecialOperation(ipN, ofg)
      case None =>
    }
  }
  
  def checkAndDoIccOperation(ofg : AndroidObjectFlowGraph[Node, ValueSet], sCfg : SuperControlFlowGraph[String]) : Boolean = {
    var flag = true
    val results = ofg.doIccOperation(this.appInfo.getDummyMainMap)

    if(results.isEmpty)
      flag = false
    else{
	    results.foreach{
	    result =>
		    if(result != null){
		      val (pi, context, targetSigs) = result
			    targetSigs.foreach{
			      targetSig =>
			        if(targetSig != null){
						    extendGraphWithConstructGraph(targetSig, pi, context.copy, ofg, sCfg)
			        }
			    }
		    }else flag = false
	    }
    }

    flag
  }
  
  def doSpecialOperation(ipN : InvokePointNode[Node], ofg : ObjectFlowGraph[Node, ValueSet]) = {
    val vsMap = ofg.doModelOperation(ipN, fac, ofg)
    vsMap.foreach{
      case (k, v) =>
        if(modelOperationValueSetMap.contains(k)){
        	val d = v.getDiff(modelOperationValueSetMap(k))
        	if(!d.isEmpty){
          	k.getProperty[ValueSet](ofg.VALUE_SET).merge(d)
          	ofg.worklist += k.asInstanceOf[Node]
        	}
        } else {
          k.getProperty[ValueSet](ofg.VALUE_SET).merge(v)
        	ofg.worklist += k.asInstanceOf[Node]
        }
    }
    modelOperationValueSetMap = vsMap
  }
  
  // callee is signature
  def extendGraphWithConstructGraph(calleeSig : String, 
      															pi : PointI, 
      															callerContext : Context,
      															ofg : AndroidObjectFlowGraph[Node, ValueSet], 
      															sCfg : SuperControlFlowGraph[String]) = {
    val points : MList[Point] = mlistEmpty
    if(ofg.isModelOperation(calleeSig)){
      val ipN = ofg.collectTrackerNodes(calleeSig, pi, callerContext.copy)
      ofg.breakPiEdges(pi, "", callerContext)
      doSpecialOperation(ipN, ofg)
    } else if(ofg.isIccOperation(calleeSig)) {
      ofg.setIccOperationTracker(calleeSig, pi, callerContext.copy)
    } else if(!processed.contains((calleeSig, callerContext))){
      if(pstMap.contains(calleeSig)){
        val cfg = cfgs(calleeSig)
        val rda = rdas(calleeSig)
        points ++= new PointsCollector().points(pstMap(calleeSig))
        ofg.points ++= points
        setProcessed(points, calleeSig, callerContext.copy)
        ofg.constructGraph(calleeSig, points, callerContext.copy, cfg, rda)        
        sCfg.collectionCfgToBaseGraph(calleeSig, cfg)
      } else {
        //get ofg ccfg from file
//        val calleeOfg = androidCache.load[ObjectFlowGraph[Node, ValueSet]](callee, "ofg")
//        val calleeCCfg = androidCache.load[CompressedControlFlowGraph[String]](callee, "cCfg")
//        calleeOfg.updateContext(callerContext)
//        processed += ((callee, callerContext.copy) -> ofg.combineOfgs(calleeOfg))
//        sCfg.collectionCCfgToBaseGraph(callee, calleeCCfg)
      }
    }
    if(processed.contains(calleeSig, callerContext)){
      val procPoint = processed(calleeSig, callerContext)
      require(procPoint != null)
      ofg.extendGraph(procPoint, pi, callerContext.copy)
      if(!ofg.isModelOperation(calleeSig) &&
         !ofg.isIccOperation(calleeSig))
      	sCfg.extendGraph(calleeSig, pi.owner, pi.locationUri, pi.locationIndex)
    } else {
      //need to extend
    }
  }
  
  def extendGraphWithConstructGraphForIcc(calleeSig : String, 
                                    pi : PointI, 
                                    context : Context,
                                    ofg : AndroidObjectFlowGraph[Node, ValueSet], 
                                    sCfg : SuperControlFlowGraph[String]) = {
    val points : MList[Point] = mlistEmpty
    if(!processed.contains(calleeSig)){
      if(pstMap.contains(calleeSig)){
        val cfg = cfgs(calleeSig)
        val rda = rdas(calleeSig)
        points ++= new PointsCollector().points(pstMap(calleeSig))
        ofg.points ++= points
        setProcessed(points, calleeSig, context)
        ofg.constructGraph(calleeSig, points, context, cfg, rda)
        sCfg.collectionCfgToBaseGraph(calleeSig, cfg)
      } else {
        //get ofg ccfg from file
//        val calleeOfg = androidCache.load[ObjectFlowGraph[Node, ValueSet]](callee, "ofg")
//        val calleeCfg = androidCache.load[CompressedControlFlowGraph[String]](callee, "cfg")
//        calleeOfg.updateContext(context)
//        processed += ((callee, context) -> ofg.combineOfgs(calleeOfg))
//        sCfg.collectionCfgToBaseGraph(callee, calleeCfg)
      }
    }
    if(processed.contains((calleeSig, context))){
      val procPoint = processed((calleeSig, context))
      require(procPoint != null)
      ofg.extendGraphForIcc(procPoint, pi, context)
//      sCfg.extendGraph(callee, pi.owner, pi.locationUri, pi.locationIndex)
    } else {
      //need to extend
    }
  }
  
  def setProcessed(points : MList[Point], calleeSig : String, context : Context) = {
    points.foreach(
      point => {
        if(point.isInstanceOf[PointProc]){
          processed += ((calleeSig, context) -> point.asInstanceOf[PointProc])
        }
      }
    )
  }
}