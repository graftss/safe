/**
 * *****************************************************************************
 * Copyright (c) 2016-2017, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ****************************************************************************
 */

package kr.ac.kaist.safe.analyzer.domain

import scala.collection.immutable.HashSet

// default primitive value abstract domain
object DefaultPValue extends PValueDomain {
  lazy val Bot: Elem =
    Elem(AbsUndef.Bot, AbsNull.Bot, AbsBool.Bot, AbsNum.Bot, AbsStr.Bot)
  lazy val Top: Elem =
    Elem(AbsUndef.Top, AbsNull.Top, AbsBool.Top, AbsNum.Top, AbsStr.Top)

  def alpha(pvalue: PValue): Elem = pvalue match {
    case Undef => Bot.copy(undefval = AbsUndef.Top)
    case Null => Bot.copy(nullval = AbsNull.Top)
    case Bool(b) => Bot.copy(boolval = AbsBool(b))
    case Num(n) => Bot.copy(numval = AbsNum(n))
    case Str(str) => Bot.copy(strval = AbsStr(str))
  }

  def apply(
    undefval: AbsUndef,
    nullval: AbsNull,
    boolval: AbsBool,
    numval: AbsNum,
    strval: AbsStr
  ): Elem = Elem(undefval, nullval, boolval, numval, strval)

  case class Elem(
      undefval: AbsUndef,
      nullval: AbsNull,
      boolval: AbsBool,
      numval: AbsNum,
      strval: AbsStr
  ) extends ElemTrait {
    def gamma: ConSet[PValue] = ConInf() // TODO more precisely

    def getSingle: ConSingle[PValue] = ConMany() // TODO more precisely

    /* partial order */
    def ⊑(that: Elem): Boolean = {
      val (left, right) = (this, that)
      if (left eq right) true
      else {
        (left.undefval ⊑ right.undefval) &&
          (left.nullval ⊑ right.nullval) &&
          (left.boolval ⊑ right.boolval) &&
          (left.numval ⊑ right.numval) &&
          (left.strval ⊑ right.strval)
      }
    }

    /* join */
    def ⊔(that: Elem): Elem = {
      val (left, right) = (this, that)
      if (left eq right) left
      else {
        Elem(
          left.undefval ⊔ right.undefval,
          left.nullval ⊔ right.nullval,
          left.boolval ⊔ right.boolval,
          left.numval ⊔ right.numval,
          left.strval ⊔ right.strval
        )
      }
    }

    /* meet */
    def ⊓(that: Elem): Elem = {
      val (left, right) = (this, that)
      Elem(
        left.undefval ⊓ right.undefval,
        left.nullval ⊓ right.nullval,
        left.boolval ⊓ right.boolval,
        left.numval ⊓ right.numval,
        left.strval ⊓ right.strval
      )
    }

    override def toString(): String = {
      var lst: List[String] = Nil

      this.undefval.fold(()) { lst ::= _.toString }
      this.nullval.fold(()) { lst ::= _.toString }
      this.boolval.fold(()) { lst ::= _.toString }
      this.numval.fold(()) { lst ::= _.toString }
      this.strval.fold(()) { lst ::= _.toString }

      lst match {
        case Nil => "⊥(primitive value)"
        case _ => lst.mkString(", ")
      }
    }

    def ===(that: Elem): AbsBool = {
      val right = that
      val falseV =
        if ((this ⊔ right).typeCount > 1) AbsBool.False
        else AbsBool.Bot
      (this.undefval === right.undefval) ⊔
        (this.nullval === right.nullval) ⊔
        (this.boolval === right.boolval) ⊔
        (this.numval === right.numval) ⊔
        (this.strval === right.strval) ⊔
        falseV
    }

    def typeCount: Int = {
      var count = 0;
      this.undefval.fold(()) { _ => count += 1 }
      this.nullval.fold(()) { _ => count += 1 }
      this.boolval.fold(()) { _ => count += 1 }
      this.numval.fold(()) { _ => count += 1 }
      this.strval.fold(()) { _ => count += 1 }
      count
    }

    def toStringSet: Set[AbsStr] = {
      var set = HashSet[AbsStr]()

      this.undefval.foldUnit(set += AbsStr("undefined"))
      this.nullval.foldUnit(set += AbsStr("null"))

      if (AbsBool.True ⊑ this.boolval) set += AbsStr("true")
      if (AbsBool.False ⊑ this.boolval) set += AbsStr("false")

      set += this.numval.toAbsStr

      this.strval.foldUnit(set += this.strval)

      // remove redundancies
      set.filter(s => !set.exists(o => s != o && s ⊑ o))
    }

    def copyWith(
      undefval: AbsUndef = this.undefval,
      nullval: AbsNull = this.nullval,
      boolval: AbsBool = this.boolval,
      numval: AbsNum = this.numval,
      strval: AbsStr = this.strval
    ): Elem = Elem(undefval, nullval, boolval, numval, strval)
  }
}
