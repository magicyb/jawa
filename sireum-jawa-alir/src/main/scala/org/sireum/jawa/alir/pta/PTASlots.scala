package org.sireum.jawa.alir.pta

import org.sireum.alir.Slot

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
abstract class PTASlot(id: Any) extends Slot {
  def getId: Any = this.id
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class VarSlot(varName: String) extends PTASlot(varName) {
  def isGlobal: Boolean = varName.startsWith("@@")
  override def toString = varName
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */
final case class BaseSlot(varName: String) extends PTASlot(varName) {
  def isGlobal: Boolean = varName.startsWith("@@")
  override def toString = varName + "+"
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
abstract class HeapSlot(ins: Instance) extends PTASlot(ins){
  def matchWithInstance(ins: Instance): Boolean = this.ins == ins
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class FieldSlot(ins: Instance, fieldName: String) extends HeapSlot(ins){
  override def toString = ins.toString + "." + fieldName
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class ArraySlot(ins: Instance) extends HeapSlot(ins){
  override def toString = ins.toString
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class InstanceSlot(ins: Instance) extends PTASlot(ins){
  override def toString = ins.toString
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class InvokeSlot(sig: String, invTyp: String) extends PTASlot(sig){
  override def toString = "Invoke: " + invTyp + " " + sig
}