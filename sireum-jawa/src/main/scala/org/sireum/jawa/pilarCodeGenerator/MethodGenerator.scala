/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.pilarCodeGenerator

import org.stringtemplate.v4.STGroupFile
import java.util.ArrayList
import org.sireum.util._
import org.sireum.jawa.JawaClass
import org.stringtemplate.v4.ST
import org.sireum.jawa.JawaMethod
import org.sireum.jawa.Center
import org.sireum.jawa.util.StringFormConverter
import org.sireum.jawa.MessageCenter._
import org.sireum.jawa.JawaResolver
import java.util.Arrays
import org.sireum.jawa.NormalType
import org.sireum.jawa.util.SignatureParser
import org.sireum.jawa.JawaCodeSource

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 */ 
abstract class MethodGenerator {
  
  private final val TITLE = "MethodGenerator"
  
  protected var currentComponent : String = null
  protected var androidClasses : Set[String] = Set()
  /**
   * Map from clazz (i.e. container class) to list of callback method
   */
  protected var callbackFunctions : Map[String, Set[String]] = Map()
  protected var conditionCounter : Int = 0
  protected var codeCounter : Int = 0
  protected val template = new STGroupFile("org/sireum/jawa/resources/pilarCodeGenerator/PilarCode.stg")
  protected val procDeclTemplate = template.getInstanceOf("ProcedureDecl")
  protected val localVarsTemplate = template.getInstanceOf("LocalVars")
  protected val bodyTemplate = template.getInstanceOf("Body")
  protected val varGen = new VariableGenerator()
  protected val localVars = new ArrayList[String]
  protected val codeFragments = new ArrayList[CodeFragmentGenerator]
  
  /**
   * map from a clazz to it's substitute clazz
   */
  protected var substituteClassMap : IMap[String, String] = imapEmpty
  
  /**
   * Map from clazz to it's local variable
   */
  protected var localVarsForClasses : Map[String, String] = Map()
  
  /**
   * Set of param's clazz name
   */
  protected var paramClasses : Set[JawaClass] = Set()

  /**
   * set the substituteClassMap
   */
  def setSubstituteClassMap(map : IMap[String, String]) = this.substituteClassMap = map
  
  /**
	 * Registers a list of classes to be automatically scanned for Android
	 * lifecycle methods
	 * @param androidClasses The list of classes to be automatically scanned for
	 * Android lifecycle methods
	 */
  def setEntryPointClasses(androidClasses : Set[String]) = {
    this.androidClasses = androidClasses
  }
  
  def setCurrentComponent(clazz : String) = {
    this.currentComponent = clazz
  }
  
    
  def setCodeCounter(codeCtr : Int) = {
    this.codeCounter = codeCtr
  }
  
   def getCodeCounter():Int = {
    this.codeCounter
  }
  
  /**
	 * Sets the list of callback functions to be integrated into the Android
	 * lifecycle
	 * @param callbackFunctions The list of callback functions to be integrated
	 * into the Android lifecycle. This is a mapping from the Android element
	 * class (activity, service, etc.) to the list of callback methods for that
	 * element.
	 */
	def setCallbackFunctions(callbackFunctions : Map[String, Set[String]]) {
		this.callbackFunctions = callbackFunctions
	}
  
	def generate(name : String) : (JawaMethod, String) = {
	  generate(List(), name)
	}
  
	/**
	 * generate environment with predefined methods list
	 */
  def generate(methods : List[String], name : String) : (JawaMethod, String) = {
    val className = this.currentComponent
    val methodName = className + "." + name
	  val annotations = new ArrayList[ST]
	  val signature = 
	    "L" + className.replaceAll("\\.", "/") + ";" + "." + name + ":" + "()V"
	  initMethodHead("void", methodName, className, signature, "STATIC")
	  val code = generateInternal(List())
    msg_normal(TITLE, "environment code:\n" + code)
    (JawaResolver.resolveMethodCode(signature, code), code)
  }
  
  def generateWithParam(params : List[String], name : String) : JawaMethod = {
    val className = this.currentComponent
    val methodName = className + "." + name
	  val annotations = new ArrayList[ST]
    var parSigStr : String = ""
    params.foreach{param => parSigStr += StringFormConverter.formatTypeToSigForm(param)}
	  val signature = 
	    "L" + className.replaceAll("\\.", "/") + ";" + "." + name + ":" + "(" + parSigStr + ")V"
	  initMethodHead("void", methodName, className, signature, "STATIC")
    val paramArray = new ArrayList[ST]
    params.indices.foreach{
      i =>
        val paramVar = template.getInstanceOf("ParamVar")
			  val p = varGen.generate(params(i))
			  localVarsForClasses += (params(i) -> p)
			  this.paramClasses += Center.resolveClass(params(i), Center.ResolveLevel.BODY)
			  paramVar.add("typ", params(i))
			  paramVar.add("name", p)
			  val annot = generateExpAnnotation("type", List("object"))
			  paramVar.add("annotations", new ArrayList[ST](Arrays.asList(annot)))
			  paramArray.add(i, paramVar)
    }
    procDeclTemplate.add("params", paramArray)
    val code = generateInternal(List())
    msg_normal(TITLE, "environment code:\n" + code)
    JawaCodeSource.addAppClassCode(className, code)
    JawaResolver.resolveMethodCode(signature, code)
  }
  
  protected def generateParamAnnotation(flag : String, params : List[String]) : ST = {
    val paramArray = new ArrayList[String]
    params.foreach(param => paramArray.add(param))
    val annot = template.getInstanceOf("annotationWithParam")
	  annot.add("flag", flag)
	  annot.add("params", paramArray)
  }
  
  protected def generateExpAnnotation(flag : String, exps : List[String]) : ST = {
    val expArray = new ArrayList[String]
    exps.foreach(exp => expArray.add(exp))
    val annot = template.getInstanceOf("annotationWithExp")
	  annot.add("flag", flag)
	  annot.add("exps", expArray)
  }
  
  protected def initMethodHead(retTyp : String, methodName : String, owner : String, signature : String, access : String) = {
	  procDeclTemplate.add("retTyp", retTyp)
	  procDeclTemplate.add("procedureName", methodName)
	  val annotations = new ArrayList[ST]
	  annotations.add(generateExpAnnotation("owner", List(owner)))
	  annotations.add(generateExpAnnotation("signature", List(signature)))
	  annotations.add(generateExpAnnotation("Access", List(access)))
	  procDeclTemplate.add("annotations", annotations)
  }
	
	def generateInternal(methods : List[String]) : String
	
	protected def generateBody() : ArrayList[String] = {
	  val body : ArrayList[String] = new ArrayList[String]
	  for(i <- 0 to codeFragments.size() - 1){
	    body.add(i, codeFragments.get(i).generate)
	  }
	  body
	}
	
	protected def generateInstanceCreation(className : String, codefg : CodeFragmentGenerator) : String = {
	  val rhs =
		  if(className == "java.lang.String"){
		    val stringAnnot = generateExpAnnotation("type", List("object"))
		    "\"\" " + stringAnnot.render() 
		  } else {
			  val newExp = template.getInstanceOf("NewExp")
			  newExp.add("name", className)
			  newExp.render()
		  }
	  val va = varGen.generate(className)
	  val variable = template.getInstanceOf("LocalVar")
	  variable.add("typ", className)
	  variable.add("name", va)
	  localVars.add(variable.render())
	  val asmt = template.getInstanceOf("AssignmentStmt")
	  asmt.add("lhs", va)
	  asmt.add("rhs", rhs)
	  codefg.setCode(asmt)
	  va
	}

	
	def generateClassConstructor(r : JawaClass, constructionStack : MSet[JawaClass], codefg : CodeFragmentGenerator) : String = {
	  constructionStack.add(r)
	  val ps = r.getMethods
	  var cons : String = null
	  val conMethods = ps.filter(p => p.isConstructor && !p.isStatic && !p.getParamTypes.contains(NormalType("java.lang.Class", 0)))
	  if(!conMethods.isEmpty){
	    val p = conMethods.minBy(_.getParamTypes.size)
	  	cons = p.getSignature
	  }
	  if(cons != null){
	    generateMethodCall(cons, "direct", localVarsForClasses(r.getName), constructionStack, codefg)
	  } else {
	    err_msg_normal(TITLE, "Warning, cannot find constructor for " + r)
	  }
	  cons
	}
	
	
	protected def generateMethodCall(pSig : String, typ : String, localClassVar : String, constructionStack : MSet[JawaClass], codefg : CodeFragmentGenerator) : Unit = {
	  val sigParser = new SignatureParser(pSig).getParamSig
    val paramNum = sigParser.getParameterNum
    val params = sigParser.getObjectParameters
    var paramVars : Map[Int, String] = Map()
    params.foreach{
	    case(i, param) =>
        var r = Center.resolveClass(param.name, Center.ResolveLevel.HIERARCHY)
        val outterClassOpt = if(r.isInnerClass) Some(r.getOuterClass) else None
        if(!r.isConcrete){
          var substClassName = this.substituteClassMap.getOrElse(r.getName, null)
          if(substClassName != null) r = Center.resolveClass(substClassName, Center.ResolveLevel.HIERARCHY)
          else if(r.isInterface) Center.getClassHierarchy.getAllImplementersOf(r).foreach(i => if(constructionStack.contains(i)) r = i)
          else if(r.isAbstract) Center.getClassHierarchy.getAllSubClassesOf(r).foreach(s => if(s.isConcrete && constructionStack.contains(s)) r = s)
        }
        // to protect from going into dead constructor create loop
        if(!r.isConcrete){
          val va = varGen.generate(r.getName)
          localVarsForClasses += (r.getName -> va)
          paramVars += (i -> va)
          err_msg_normal(TITLE, "Cannot create valid constructer for " + r + ", because it is " + r.getAccessFlagString + " and cannot find substitute.")
        } else if(!constructionStack.contains(r)){
				  val va = generateInstanceCreation(r.getName, codefg)
				  localVarsForClasses += (r.getName -> va)
          paramVars += (i -> va)
          generateClassConstructor(r, constructionStack, codefg)
        } else {
          paramVars += (i -> localVarsForClasses(r.getName))
        }
    }
    val invokeStmt = template.getInstanceOf("InvokeStmt")
    invokeStmt.add("funcName", StringFormConverter.getMethodNameFromMethodSignature(pSig))
    val finalParamVars : ArrayList[String] = new ArrayList[String]
    finalParamVars.add(0, localClassVar)
    var index = 0
    for(i <- 0 to paramNum - 1){
      if(paramVars.contains(i)){
        finalParamVars.add(index + 1, paramVars(i))
      } else {
        finalParamVars.add(index + 1, "x")
        val paramName = sigParser.getParameterTypes()(i).name
        if(paramName == "double" || paramName == "long"){
          index += 1
          finalParamVars.add(index + 1, "x")
        }
      }
      index += 1
    }
    invokeStmt.add("params", finalParamVars)
    val annotations = new ArrayList[ST]
	  annotations.add(generateExpAnnotation("signature", List(pSig)))
	  annotations.add(generateExpAnnotation("type", List(typ)))
    invokeStmt.add("annotations", annotations)
    codefg.setCode(invokeStmt)
	}
	
	protected def generateCallToAllCallbacks(callbackClass : JawaClass, callbackMethods : Set[JawaMethod], classLocalVar : String, codefg : CodeFragmentGenerator) = {
	  var oneCallBackFragment = codefg
	  callbackMethods.foreach{
	    callbackMethod =>
	      val pSig = callbackMethod.getSignature
	      val thenStmtFragment = new CodeFragmentGenerator
	      createIfStmt(thenStmtFragment, oneCallBackFragment)
	      val elseStmtFragment = new CodeFragmentGenerator
	      createGotoStmt(elseStmtFragment, oneCallBackFragment)
	      thenStmtFragment.addLabel
	      codeFragments.add(thenStmtFragment)
	      generateMethodCall(pSig, "virtual", classLocalVar, msetEmpty + callbackClass, thenStmtFragment)
	      elseStmtFragment.addLabel
	      codeFragments.add(elseStmtFragment)
	      oneCallBackFragment = new CodeFragmentGenerator
		    oneCallBackFragment.addLabel
		    codeFragments.add(oneCallBackFragment)
	  }
	}
	
	protected def searchAndBuildMethodCall(subsignature : String, clazz : JawaClass, entryPoints : MList[String], constructionStack : MSet[JawaClass], codefg : CodeFragmentGenerator) = {
	  val apopt = findMethod(clazz, subsignature)
	  apopt match{
	    case Some(ap) =>
	      entryPoints -= ap.getSignature
		    assert(ap.isStatic || localVarsForClasses(clazz.getName) != null)
		    generateMethodCall(ap.getSignature, "virtual", localVarsForClasses(clazz.getName), constructionStack, codefg)
	    case None =>
	      err_msg_normal(TITLE, "Could not find Android entry point method: " + subsignature)
	      null
	  }
	}
	
	/**
	 * Generates invocation statements for all callback methods which need to be invoked during the give clazz's run cycle.
	 * @param rUri Current clazz resource uri which under process
	 * @param classLocalVar The local variable fro current clazz
	 */
	protected def addCallbackMethods(clazz : JawaClass, parentClassLocalVar : String, codefg : CodeFragmentGenerator) : Unit = {
	  if(!this.callbackFunctions.contains(clazz.getName)) return
	  var callbackClasses : Map[JawaClass, ISet[JawaMethod]] = Map()
    this.callbackFunctions(clazz.getName).map{
	    case (pSig) => 
	      val theClass = Center.resolveClass(StringFormConverter.getClassNameFromMethodSignature(pSig), Center.ResolveLevel.BODY)
	      val theMethod = findMethod(theClass, Center.getSubSigFromMethodSig(pSig))
	      theMethod match {
	        case Some(method) =>
			      callbackClasses += (theClass -> (callbackClasses.getOrElse(theClass, isetEmpty) + method))
	        case None =>
	          err_msg_normal(TITLE, "Could not find callback method " + pSig)
	      }
	  }
	  var oneCallBackFragment = codefg
		callbackClasses.foreach{
		  case(callbackClass, callbackMethods) =>
		    
		    val classLocalVar : String =
		      if(isCompatible(clazz, callbackClass)) parentClassLocalVar
		      // create a new instance of this class
		      else if(callbackClass.isConcrete){
			      val va = generateInstanceCreation(callbackClass.getName, oneCallBackFragment)
		        this.localVarsForClasses += (callbackClass.getName -> va)
		        generateClassConstructor(callbackClass, msetEmpty +clazz, oneCallBackFragment)
		        va
		      } else null
		    if(classLocalVar != null){
		      // build the calls to all callback methods in this clazz
		      generateCallToAllCallbacks(callbackClass, callbackMethods, classLocalVar, oneCallBackFragment)
		    } else {
		      err_msg_normal(TITLE, "Constructor cannot be generated for callback class " + callbackClass)
		    }
		    oneCallBackFragment = new CodeFragmentGenerator
		    oneCallBackFragment.addLabel
		    codeFragments.add(oneCallBackFragment)
		}
	}
	
	protected def isCompatible(actual : JawaClass, expected : JawaClass) : Boolean = {
	  var act : JawaClass = actual
	  while(act != null){
	    if(act.getName.equals(expected.getName))
	      return true
	    if(expected.isInterface)
	      act.getInterfaces.foreach{int => if(int.getName.equals(expected.getName)) return true}
	    if(!act.hasSuperClass)
	      act = null
	    else act = act.getSuperClass
	  }
	  false
	}
	
	protected def createIfStmt(targetfg : CodeFragmentGenerator, codefg : CodeFragmentGenerator) = {
	  val target = targetfg.getLabel
	  if(target != null){
	    val condExp = template.getInstanceOf("CondExp")
      condExp.add("lhs", "RandomCoinToss")
      condExp.add("rhs", "head")
      val ifStmt = template.getInstanceOf("IfStmt")
      ifStmt.add("cond", condExp)
      ifStmt.add("label", target)
      codefg.setCode(ifStmt)
	  }
	}
	
	protected def createGotoStmt(targetfg : CodeFragmentGenerator, codefg : CodeFragmentGenerator) = {
	  val target = targetfg.getLabel
	  if(target != null){
      val gotoStmt = template.getInstanceOf("GotoStmt")
      gotoStmt.add("label", target)
      codefg.setCode(gotoStmt)
	  }
	}
	
	protected def createReturnStmt(variable : String, codefg : CodeFragmentGenerator) = {
	  val returnStmt = template.getInstanceOf("ReturnStmt")
      returnStmt.add("variable", variable)
      codefg.setCode(returnStmt)
	}
	
	protected def createFieldSetStmt(base : String, field : String, rhs : String, annoTyps : List[String], codefg : CodeFragmentGenerator) = {
    val mBaseField = template.getInstanceOf("FieldAccessExp")
	  mBaseField.add("base", base)
	  mBaseField.add("field", field)
	  val asmt = template.getInstanceOf("AssignmentStmt")
	  asmt.add("lhs", mBaseField)
	  asmt.add("rhs", rhs)
	  val annos = generateExpAnnotation("type", annoTyps)
	  asmt.add("annotations", annos)
	  codefg.setCode(asmt)
	}
	
	protected class CodeFragmentGenerator {
	  protected val codeFragment = template.getInstanceOf("CodeFragment")
	  protected val codes : ArrayList[ST] = new ArrayList[ST]
	  protected var label = template.getInstanceOf("Label")
	  
	  def addLabel() = {
	    label.add("num", conditionCounter)
	    codeFragment.add("label", label)
	    conditionCounter += 1
	  }
	  def getLabel() : ST = label
	  def setCode(code : ST) = {
	    codes.add(code)
	  }
	  def generate() : String = {
	    val finalCodes = new ArrayList[ST]
	    for(i <- 0 to codes.size - 1){
	      val code = template.getInstanceOf("Code")
	      code.add("num", codeCounter)
	      codeCounter += 1
	      code.add("code", codes.get(i))
	      finalCodes.add(i, code)
	    }
	    codeFragment.add("codes", finalCodes)
	    codeFragment.render()
	  }
	}
	
	protected def findMethod(currentClass : JawaClass, subSig : String) : Option[JawaMethod] = {
	  if(currentClass.declaresMethod(subSig)) Some(currentClass.getMethod(subSig))
	  else if(currentClass.hasSuperClass) findMethod(currentClass.getSuperClass, subSig)
	  else None
	}
}