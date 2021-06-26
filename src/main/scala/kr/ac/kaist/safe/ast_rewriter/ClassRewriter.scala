/**
 * *****************************************************************************
 * Copyright (c) 2016-2018, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ****************************************************************************
 */

package kr.ac.kaist.safe.ast_rewriter

import kr.ac.kaist.safe.errors.ExcLog
import kr.ac.kaist.safe.errors.error._
import kr.ac.kaist.safe.errors.warning._
import kr.ac.kaist.safe.nodes.ast._
import kr.ac.kaist.safe.parser.Parser
import kr.ac.kaist.safe.util.{ NodeUtil => NU, Span }

class ClassRewriter(program: Program) {
  val result: Program = {
    NU.SimplifyWalker.walk(ClassRewriteWalker.walk(program))
  }

  // helper methods for creating AST nodes with less boilerplate
  def id(info: ASTNodeInfo, text: String): Id = Id(info, text, None, false)
  def varRef(info: ASTNodeInfo, text: String): VarRef = VarRef(info, id(info, text))

  // decide if a `ClassMethod` is the constructor by its name
  def isConstructor(cm: ClassMethod): Boolean = cm.ftn.name.text == "constructor"

  // creates an empty functional (no statements or arguments) with the given `name`
  def emptyFunctional(info: ASTNodeInfo, name: Id) = Functional(
    info,
    List(), // no internal function declarations
    List(), // no internal variable declarations
    Stmts(info, List(), false), // no statement in the body
    name,
    List(), // no arguments
    "", // empty stringified function body
    false // not in strict mode
  )

  // creates an LHS expression involving `[className].prototype`.
  def prototypeLHS(info: ASTNodeInfo, className: Id, methodName: Option[Id]): LHS = {
    // `[className].prototype`
    val proto = Dot(info, VarRef(info, className), id(info, "prototype"))

    methodName match {
      // `[className].prototype.[methodName]`
      case Some(value) => Dot(info, proto, value)
      case None => proto
    }
  }

  // builds two statements that implement ES5 class inheritance.
  def inheritanceStmts(className: Id, superClass: LHS): List[Stmt] = {
    val info = superClass.info

    // [className].prototype = Object.create([superClass].prototype)
    val proto = prototypeLHS(className.info, className, None)
    val objCreate = Dot(info, varRef(info, "Object"), id(info, "create"))
    val superProto = Dot(info, superClass, id(info, "prototype"))
    val objCreateCall = FunApp(info, objCreate, List(superProto))
    val assignment = AssignOpApp(info, proto, Op(info, "="), objCreateCall)
    val stmt1 = ExprStmt(info, assignment, false)

    // [className].prototype.constructor = [className]
    val protoCons = prototypeLHS(className.info, className, Some(id(className.info, "constructor")))
    val consAssignment = AssignOpApp(info, protoCons, Op(info, "="), VarRef(info, className))
    val stmt2 = ExprStmt(info, consAssignment, false)

    List(stmt1, stmt2)
  }

  private object ClassRewriteWalker extends ASTWalker {
    override def walk(stmt: Stmt): Stmt = stmt match {
      case ClassDeclaration(info, className, superClass, methods) =>
        // build the constructor, which is a function declaration
        val constructor = methods.find(isConstructor) match {
          // splice the body of an explicit `constructor` method into
          // a function declaration named `className`.
          case Some(cm) => FunDecl(info, cm.ftn.copy(name = className), strict = false)

          // if no explicit constructor is given, create an empty function declaration
          // to serve as the constructor.
          case None => FunDecl(info, emptyFunctional(info, className), strict = false)
        }

        val inheritance: List[Stmt] = superClass match {
          case None => List()
          case Some(_superClass) => inheritanceStmts(className, _superClass)
        }

        // build the method definitions as assignments to the constructor's prototype.
        // i.e. `[className].prototype.[methodName] = [method function definition]`
        val methodDefns: List[Stmt] = methods.filterNot(isConstructor).map(method => {
          val lhs = prototypeLHS(method.info, className, Some(method.ftn.name))
          val methodFunExpr = FunExpr(method.ftn.info, method.ftn)
          val assignment = AssignOpApp(method.info, lhs, Op(method.info, "="), methodFunExpr)
          ExprStmt(method.info, assignment, false)
        })

        // roll the constructor and the methods together into a single block of statements.
        val stmts = constructor :: inheritance ++ methodDefns
        ABlock(info, stmts, false)

      case _ => super.walk(stmt)
    }
  }
}
