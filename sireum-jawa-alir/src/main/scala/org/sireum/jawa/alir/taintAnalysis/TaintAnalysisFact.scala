package org.sireum.jawa.alir.taintAnalysis

import org.sireum.alir.Slot
import org.sireum.jawa.alir.reachingFactsAnalysis.RFAFact

final case class TaintFact(fact : RFAFact, source : String){
  override def toString : String = {
    "TaintFact" + "(" + fact + "->" + source + ")"
  }
}