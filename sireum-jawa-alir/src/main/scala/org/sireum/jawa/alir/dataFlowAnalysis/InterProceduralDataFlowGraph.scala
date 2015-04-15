/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.alir.dataFlowAnalysis

import org.sireum.jawa.alir.controlFlowGraph._
import org.sireum.jawa.alir.pta.reachingFactsAnalysis.RFAFact
import org.sireum.jawa.alir.pta.PTAResult

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
case class InterProceduralDataFlowGraph(icfg : InterproceduralControlFlowGraph[ICFGNode], ptaresult : PTAResult){
  def merge(idfg: InterProceduralDataFlowGraph) = {
    this.icfg.merge(idfg.icfg)
    this.ptaresult.merge(idfg.ptaresult)
  }
}