/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.alir.interProcedural

import org.sireum.jawa.alir.controlFlowGraph._
import org.sireum.jawa.alir.reachingFactsAnalysis.RFAFact

case class InterProceduralDataFlowGraph(icfg : InterproceduralControlFlowGraph[CGNode], summary : InterProceduralMonotoneDataFlowAnalysisResult[RFAFact])