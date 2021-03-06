/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
abstract class Type {
	def typ : String
	def dimensions : Int
	def isArray : Boolean
	def name : String
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class NormalType(val typ : String, val dimensions : Int) extends Type {
  def this(typ : String) = this(typ, 0)
  def isArray = dimensions > 0
  def name : String = {
    val sb = new StringBuilder
    sb.append(typ)
    for(i <- 0 to dimensions - 1) sb.append("[]")
    sb.toString.intern()
  }
  override def toString : String = {
    name
  }
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class TupleType(val left : Type, val right : Type) extends Type {
  def typ : String = {
    val sb = new StringBuilder
    sb.append("{" + left + "," + right + "}")
    sb.toString.intern()
  }
  def dimensions = 0
  def isArray = dimensions > 0
  def name : String = {
    val sb = new StringBuilder
    sb.append(typ)
    sb.toString.intern()
  }
  override def toString : String = {
    val sb = new StringBuilder
    sb.append(typ)
    for(i <- 0 to dimensions - 1) sb.append("[]")
    sb.toString.intern()
  }
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class NullType() extends Type {
  def typ = "Null"
  def dimensions = 0
  def isArray = false
  def name : String = {
    val sb = new StringBuilder
    sb.append(typ)
    for(i <- 0 to dimensions - 1) sb.append("[]")
    sb.toString.intern()
  }
  override def toString : String = {
    val sb = new StringBuilder
    sb.append(typ)
    sb.toString.intern()
  }
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
//final case class UnknownType() extends Type {
//  def typ = "Unknown"
//  def dimensions = 0
//  def isArray = false
//  def name : String = {
//    val sb = new StringBuilder
//    sb.append(typ)
//    for(i <- 0 to dimensions - 1) sb.append("[]")
//    sb.toString.intern()
//  }
//  override def toString : String = {
//    val sb = new StringBuilder
//    sb.append(typ)
//    sb.toString.intern()
//  }
//}