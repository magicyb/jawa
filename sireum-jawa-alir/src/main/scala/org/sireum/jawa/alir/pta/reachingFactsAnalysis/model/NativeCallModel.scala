/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.alir.pta.reachingFactsAnalysis.model

import org.sireum.jawa.JawaMethod
import org.sireum.util._
import org.sireum.jawa.alir.pta.reachingFactsAnalysis._
import org.sireum.jawa.Center
import org.sireum.jawa.alir.Context
import org.sireum.jawa.alir.JawaAlirInfoProvider
import org.sireum.jawa.alir.pta._

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 */ 
object NativeCallModel {
	 def isNativeCall(p : JawaMethod) : Boolean = p.isNative
	 
	 def doNativeCall(s : PTAResult, p : JawaMethod, args : List[String], retVars : Seq[String], currentContext : Context) : (ISet[RFAFact], ISet[RFAFact], Boolean) = {
	  var newFacts = isetEmpty[RFAFact]
	  var delFacts = isetEmpty[RFAFact]
	  var byPassFlag = true
	  	  
	  p.getSignature match{
	    case "Ljava/lang/Object;.getClass:()Ljava/lang/Class;" =>
	      // algo:thisvalue.foreach {ins => set insRec's classObj field with a classIns whose type is java:lang:Class and name is same as ins's type
	               // then, create two facts (a) (retVarSlot, insRec.classObj), (b) ([insRec.classObj, "java:lang:Class.name"], concreteString(ins.typ))}
	      require(args.size > 0)
        val thisSlot = VarSlot(args(0))
	      val thisValue = s.pointsToSet(thisSlot, currentContext)
	      thisValue.foreach{
	        ins =>
	          require(Center.hasClass(ins.typ.typ))
	          val insRec = Center.getClass(ins.typ.typ)
	          val insClasObj = JawaAlirInfoProvider.getClassInstance(insRec)
	          newFacts += RFAFact(VarSlot(retVars(0)), insClasObj)
	          val strIns = PTAConcreteStringInstance(insClasObj.getName, insClasObj.defSite)
	          newFacts += (RFAFact(FieldSlot(insClasObj, "java.lang.Class.name"), strIns))
	      }
	      byPassFlag = false
	    case "Ljava/lang/Class;.getNameNative:()Ljava/lang/String;" =>
	      // algo:thisValue.foreach.{ cIns => get value of (cIns.name") and create fact (retVar, value)}
	      require(args.size > 0)
        val thisSlot = VarSlot(args(0))
	      val thisValue = s.pointsToSet(thisSlot, currentContext)
	      thisValue.foreach{
	        cIns =>
	          println(cIns + " " + cIns.getClass())
	          require(cIns.isInstanceOf[ClassInstance])
	          val name = cIns.asInstanceOf[ClassInstance].getName
	          val strIns = PTAConcreteStringInstance(name, cIns.defSite)
              newFacts += (RFAFact(VarSlot(retVars(0)), strIns))
	      }
	      byPassFlag = false
	    case _ =>
	  }
	  (newFacts, delFacts, byPassFlag)
	}
}