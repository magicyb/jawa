package org.sireum.amandroid.interProcedural.reachingFactsAnalysis.model

import org.sireum.amandroid.AmandroidProcedure
import org.sireum.util._
import org.sireum.amandroid.AmandroidRecord
import org.sireum.amandroid.Type
import org.sireum.amandroid.interProcedural.Context
import org.sireum.amandroid.Center
import org.sireum.amandroid.NormalType
import org.sireum.alir.Slot
import org.sireum.amandroid.Instance
import org.sireum.amandroid.interProcedural.reachingFactsAnalysis._

/**
 * @author Fengguo Wei & Sankardas Roy
 */
trait ModelCallHandler {
  
  /**
   * return true if the given callee procedure needs to be modeled
   */
  def isModelCall(calleeProc : AmandroidProcedure) : Boolean = {
	  val r = calleeProc.getDeclaringRecord
	  StringBuilderModel.isStringBuilder(r) ||
	  StringModel.isString(r) || 
	  HashSetModel.isHashSet(r) || 
	  HashtableModel.isHashtable(r) ||
	  HashMapModel.isHashMap(r) ||
	  NativeCallModel.isNativeCall(calleeProc)
	  
  }
      
  /**
   * instead of doing operation inside callee procedure's real code, we do it manually and return the result. 
   */
	def doModelCall(s : ISet[RFAFact], calleeProc : AmandroidProcedure, args : List[String], retVarOpt : Option[String], currentContext : Context) : ISet[RFAFact] = {
	  val r = calleeProc.getDeclaringRecord
	  if(StringModel.isString(r)) StringModel.doStringCall(s, calleeProc, args, retVarOpt, currentContext)
	  else if(StringBuilderModel.isStringBuilder(r)) StringBuilderModel.doStringBuilderCall(s, calleeProc, args, retVarOpt, currentContext)
	  else if(HashSetModel.isHashSet(r)) HashSetModel.doHashSetCall(s, calleeProc, args, retVarOpt, currentContext)
	  else if(HashtableModel.isHashtable(r)) HashtableModel.doHashtableCall(s, calleeProc, args, retVarOpt, currentContext)
	  else if(HashMapModel.isHashMap(r)) HashMapModel.doHashMapCall(s, calleeProc, args, retVarOpt, currentContext)
	  else if(NativeCallModel.isNativeCall(calleeProc)) NativeCallModel.doNativeCall(s, calleeProc, args, retVarOpt, currentContext)
	  else throw new RuntimeException("given callee is not a model call: " + calleeProc)
	}
}

object NormalModelCallHandler extends ModelCallHandler