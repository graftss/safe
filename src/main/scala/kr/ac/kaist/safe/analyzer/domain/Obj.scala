/**
 * *****************************************************************************
 * Copyright (c) 2016, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ****************************************************************************
 */

package kr.ac.kaist.safe.analyzer.domain

import kr.ac.kaist.safe.LINE_SEP
import kr.ac.kaist.safe.analyzer.models.builtin.{ BuiltinString, _ }
import kr.ac.kaist.safe.nodes.cfg.FunctionId
import kr.ac.kaist.safe.util.Loc

import scala.collection.immutable.{ HashMap, HashSet }

//TODO: Merge ObjMap implementation
//TODO: Handle default values, key values with "@"
class Obj(val map: Map[String, (PropValue, Absent)]) {
  override def toString: String = {
    val sortedMap = map.toSeq.sortBy {
      case (key, _) => key
    }

    val s = new StringBuilder
    sortedMap.map {
      case (key, (propv, absent)) => {
        s.append(key).append(absent match {
          case AbsentTop => s" @-> "
          case AbsentBot => s" |-> "
        }).append(propv.toString).append(LINE_SEP)
      }
    }

    s.toString
  }

  /* partial order */
  def <=(that: Obj): Boolean = {
    if (this.map.isEmpty) true
    else if (that.map.isEmpty) false
    else if (!(this.map.keySet subsetOf that.map.keySet)) false
    else that.map.forall(kv => {
      val (key, thatPVA) = kv
      this.map.get(key) match {
        case None => false
        case Some(thisPVA) =>
          val (thisPV, thisAbsent) = thisPVA
          val (thatPV, thatAbsent) = thatPVA
          thisPV <= thatPV && thisAbsent <= thatAbsent
      }
    })
  }

  /* not a partial order */
  def </(that: Obj): Boolean = !(this <= that)

  /* join */
  def +(that: Obj): Obj = {
    val keys = this.map.keySet ++ that.map.keySet
    val newMap = keys.foldLeft(Obj.ObjMapBot)((m, key) => {
      val thisVal = this.map.get(key)
      val thatVal = that.map.get(key)
      (thisVal, thatVal) match {
        case (None, None) => m
        case (None, Some(v)) =>
          val (prop, _) = v
          m + (key -> (prop, AbsentTop))
        case (Some(v), None) =>
          val (prop, _) = v
          m + (key -> (prop, AbsentTop))
        case (Some(v1), Some(v2)) =>
          val (propV1, absent1) = v1
          val (propV2, absent2) = v2
          m + (key -> (propV1 + propV2, absent1 + absent2))
      }
    })
    new Obj(newMap)
  }

  /* lookup */
  private def lookup(x: String): (Option[PropValue], Absent) = {
    this.map.get(x) match {
      case Some(pva) =>
        val (propV, absent) = pva
        (Some(propV), absent)
      case None if x.take(1) == "@" => (None, AbsentBot)
      case None if isNum(x) =>
        val (propV, absent) = map(STR_DEFAULT_NUMBER)
        (Some(propV), absent)
      case None if !isNum(x) =>
        val (propV, absent) = map(STR_DEFAULT_OTHER)
        (Some(propV), absent)
    }
  }

  /* meet */
  def <>(that: Obj): Obj = {
    if (this.map eq that.map) this
    else {
      val map1 = that.map.foldLeft(this.map)((m, kv) => {
        val (key, thatPVA) = kv
        val (thatPV, thatAbsent) = thatPVA
        val (thisPVOpt, thisAbsent) = this.lookup(key)
        thisPVOpt match {
          case Some(thisPV) if m.contains(key) => m + (key -> (thisPV <> thatPV, thisAbsent <> thatAbsent))
          case _ => m - key
        }
      })
      val map2 = this.map.foldLeft(map1)((m, kv) => {
        val (key, thisPVA) = kv
        if (that.map.contains(key)) m
        else m - key
      })
      new Obj(map2)
    }
  }

  def isBottom: Boolean = {
    if (this.map.isEmpty) true
    else if ((this.map.keySet diff DEFAULT_KEYSET).nonEmpty) false
    else
      this.map.foldLeft(true)((b, kv) => {
        val (_, pva) = kv
        val (propV, absent) = pva
        b && propV.isBottom && absent.isBottom
      })
  }

  /* substitute locR by locO */
  def subsLoc(locR: Loc, locO: Loc): Obj = {
    if (this.map.isEmpty) this
    else {
      val newMap = this.map.foldLeft(Obj.ObjMapBot)((m, kv) => {
        val (key, pva) = kv
        val (propV, absent) = pva
        val newV = propV.objval.value.subsLoc(locR, locO)
        val newOV = ObjectValue(newV, propV.objval.writable, propV.objval.enumerable, propV.objval.configurable)
        val newPropV = PropValue(newOV, propV.funid)
        m + (key -> (newPropV, absent))
      })
      new Obj(newMap)
    }
  }

  def weakSubsLoc(locR: Loc, locO: Loc): Obj = {
    if (this.map.isEmpty) this
    else {
      val newMap = this.map.foldLeft(Obj.ObjMapBot)((m, kv) => {
        val (key, pva) = kv
        val (propV, absent) = pva
        val newV = propV.objval.value.weakSubsLoc(locR, locO)
        val newOV = ObjectValue(newV, propV.objval.writable, propV.objval.enumerable, propV.objval.configurable)
        val newPropV = PropValue(newOV, propV.funid)
        m + (key -> (newPropV, absent))
      })
      new Obj(newMap)
    }
  }

  def apply(s: String): Option[PropValue] = {
    this.map.get(s) match {
      case Some(pva) =>
        val (propV, _) = pva
        Some(propV)
      case None if s.take(1) == "@" => None
      case None if DEFAULT_KEYSET contains s => None
      case None if isNum(s) => this(STR_DEFAULT_NUMBER)
      case None if !isNum(s) => this(STR_DEFAULT_OTHER)
    }
  }

  def apply(absStr: AbsString): Option[PropValue] = {
    def addPropOpt(opt1: Option[PropValue], opt2: Option[PropValue]): Option[PropValue] =
      (opt1, opt2) match {
        case (Some(propV1), Some(propV2)) => Some(propV1 + propV2)
        case (Some(_), None) => opt1
        case (None, Some(_)) => opt2
        case (None, None) => None
      }

    absStr.gamma match {
      case ConSetCon(strSet) if strSet.size == 1 => apply(strSet.head)
      case ConSetCon(strSet) if strSet.size > 1 => strSet.map(apply(_)).reduce(addPropOpt(_, _))
      case ConSetBot() => None
      case ConSetTop() =>
        val opt1 = absStr.gammaIsAllNums match {
          case ConSingleBot() | ConSingleCon(false) => None
          case ConSingleCon(true) | ConSingleTop() =>
            val pset = map.keySet.filter(x => !(x.take(1) == "@") && isNum(x))
            val optSet = pset.map((x) => apply(x))
            val opt1 =
              if (optSet.size > 1) optSet.reduce(addPropOpt(_, _))
              else if (optSet.size == 1) optSet.head
              else None
            this.map.get(STR_DEFAULT_NUMBER) match {
              case Some((propv, _)) => addPropOpt(Some(propv), opt1)
              case None => opt1
            }
        }
        val opt2 = absStr.gammaIsAllNums match {
          case ConSingleBot() | ConSingleCon(true) => None
          case ConSingleCon(false) | ConSingleTop() =>
            val pset = map.keySet.filter(x => !(x.take(1) == "@") && !isNum(x))
            val optSet = pset.map((x) => apply(x))
            val opt1 =
              if (optSet.size > 1) optSet.reduce(addPropOpt(_, _))
              else if (optSet.size == 1) optSet.head
              else None
            this.map.get(STR_DEFAULT_OTHER) match {
              case Some((propv, _)) => addPropOpt(Some(propv), opt1)
              case None => opt1
            }
        }
        addPropOpt(opt1, opt2)
    }
  }

  def getOrElse[T](s: String)(default: T)(f: PropValue => T): T = {
    this(s) match {
      case Some(propV) => f(propV)
      case None => default
    }
  }

  def getOrElse[T](absStr: AbsString)(default: T)(f: PropValue => T): T = {
    this(absStr) match {
      case Some(propV) => f(propV)
      case None => default
    }
  }

  def get(s: String)(utils: Utils): PropValue = {
    this(s) match {
      case Some(propV) => propV
      case None => PropValue.Bot(utils)
    }
  }

  def -(s: String): Obj = {
    if (this.isBottom) this
    else Obj(this.map - s)
  }

  def -(absStr: AbsString)(utils: Utils): Obj = {
    val (defaultNumber, _) = this.map(STR_DEFAULT_NUMBER)
    val (defaultOther, _) = this.map(STR_DEFAULT_OTHER)
    absStr.gamma match {
      case _ if this.isBottom => this
      case ConSetBot() => Obj.Bot(utils)
      case ConSetTop() =>
        val properties = this.map.keySet.filter(x => {
          val isInternalProp = x.take(1) == "@"
          val (prop, _) = this.map(x)
          val configurable = utils.absBool.True <= prop.objval.configurable
          (!isInternalProp) && configurable
        })
        val newMap = properties.foldLeft(this.map)((tmpMap, x) => {
          val (prop, _) = tmpMap(x)
          if (isNum(x) && prop <= defaultNumber) tmpMap - x
          else if (!isNum(x) && prop <= defaultOther) tmpMap - x
          else tmpMap.updated(x, (prop, AbsentTop))
        })
        Obj(newMap)
      case ConSetCon(strSet) if strSet.size == 1 => this - strSet.head
      case ConSetCon(strSet) =>
        val newMap = strSet.foldLeft(this.map)((tmpMap, x) => {
          tmpMap.get(x) match {
            case None => tmpMap
            case Some(pva) =>
              val (prop, _) = pva
              if (isNum(x) && prop <= defaultNumber) tmpMap - x
              else if (!isNum(x) && prop <= defaultOther) tmpMap - x
              else tmpMap.updated(x, (prop, AbsentTop))
          }
        })
        Obj(newMap)
    }
  }

  // absent value is set to AbsentBot because it is strong update.
  def update(x: String, propv: PropValue, exist: Boolean = false): Obj = {
    if (this.isBottom)
      this
    else if (x.startsWith("@default"))
      Obj(map.updated(x, (propv, AbsentTop)))
    else
      Obj(map.updated(x, (propv, AbsentBot)))
  }

  def update(absStr: AbsString, propV: PropValue, utils: Utils): Obj = {
    absStr.gamma match {
      case ConSetCon(strSet) if strSet.size == 1 => // strong update
        Obj(map.updated(strSet.head, (propV, AbsentBot)))
      case ConSetCon(strSet) =>
        strSet.foldLeft(this)((r, x) => r + update(x, propV))
      case ConSetBot() => Obj.Bot(utils)
      case ConSetTop() => absStr.gammaIsAllNums match {
        case ConSingleBot() => Obj.Bot(utils)
        case ConSingleCon(true) =>
          val newDefaultNum = this.map.get(STR_DEFAULT_NUMBER) match {
            case Some((numPropV, _)) => numPropV + propV
            case None => propV
          }
          val pset = map.keySet.filter(x => map.get(x) match {
            case Some((xPropV, _)) => !(x.take(1) == "@") && isNum(x) && utils.absBool.True <= xPropV.objval.writable
            case None => false
          })
          val weakUpdatedMap = pset.foldLeft(this.map)((m, x) => {
            val absX = utils.absString.alpha(x)
            val (xPropV, xAbsent) = m.get(x) match {
              case Some((xPropV, xAbsent)) => (propV + xPropV, xAbsent)
              case None => (propV, AbsentBot)
            }
            if (AbsentTop <= xAbsent && absX.isAllNums) m - x
            else m + (x -> (xPropV, xAbsent))
          })
          Obj(weakUpdatedMap + (STR_DEFAULT_NUMBER -> (newDefaultNum, AbsentTop)))
        case ConSingleCon(false) =>
          val newDefaultOther = this.map.get(STR_DEFAULT_OTHER) match {
            case Some((otherPropV, _)) => otherPropV + propV
            case None => propV
          }
          val pset = map.keySet.filter(x => map.get(x) match {
            case Some((xPropV, _)) => !(x.take(1) == "@") && !isNum(x) && utils.absBool.True <= xPropV.objval.writable
            case None => false
          })
          val weakUpdatedMap = pset.foldLeft(this.map)((m, x) => {
            val absX = utils.absString.alpha(x)
            val (xPropV, xAbsent) = m.get(x) match {
              case Some((xPropV, xAbsent)) => (propV + xPropV, xAbsent)
              case None => (propV, AbsentBot)
            }
            if (AbsentTop <= xAbsent && absX.isAllOthers) m - x
            else m + (x -> (xPropV, xAbsent))
          })
          Obj(weakUpdatedMap + (STR_DEFAULT_OTHER -> (newDefaultOther, AbsentTop)))
        case ConSingleTop() =>
          val newDefaultNum = this.map.get(STR_DEFAULT_NUMBER) match {
            case Some((numPropV, _)) => numPropV + propV
            case None => propV
          }
          val newDefaultOther = this.map.get(STR_DEFAULT_OTHER) match {
            case Some((otherPropV, _)) => otherPropV + propV
            case None => propV
          }
          val pset = map.keySet.filter(x => map.get(x) match {
            case Some((xPropV, _)) => !(x.take(1) == "@") && utils.absBool.True <= xPropV.objval.writable
            case None => false
          })
          val weakUpdatedMap = pset.foldLeft(this.map)((m, x) => {
            val absX = utils.absString.alpha(x)
            val (xPropV, xAbsent) = m.get(x) match {
              case Some((xPropV, xAbsent)) => (propV + xPropV, xAbsent)
              case None => (propV, AbsentBot)
            }
            if (AbsentTop <= xAbsent && absX.isAllNums && xPropV <= newDefaultNum) m - x
            else if (AbsentTop <= xAbsent && absX.isAllOthers && xPropV <= newDefaultOther) m - x
            else m + (x -> (xPropV, xAbsent))
          })
          Obj(weakUpdatedMap +
            (STR_DEFAULT_NUMBER -> (newDefaultNum, AbsentTop),
              STR_DEFAULT_OTHER -> (newDefaultOther, AbsentTop)))
      }
    }
  }

  def domIn(x: String)(absBool: AbsBoolUtil): AbsBool = {
    def defaultDomIn(default: String): AbsBool = {
      this.map.get(default) match {
        case Some((defaultPropV, _)) if !defaultPropV.objval.value.isBottom => absBool.Top
        case _ => absBool.False
      }
    }

    this.map.get(x) match {
      case Some((propV, AbsentBot)) if !propV.isBottom => absBool.True
      case Some((propV, AbsentTop)) if !propV.isBottom & x.take(1) == "@" => absBool.True
      case Some((propV, AbsentTop)) if !propV.isBottom => absBool.Top
      case Some((propV, _)) if x.take(1) == "@" => absBool.False
      case Some((propV, _)) if propV.isBottom & isNum(x) => defaultDomIn(STR_DEFAULT_NUMBER)
      case Some((propV, _)) if propV.isBottom & !isNum(x) => defaultDomIn(STR_DEFAULT_OTHER)
      case None if x.take(1) == "@" => absBool.False
      case None if isNum(x) => defaultDomIn(STR_DEFAULT_NUMBER)
      case None if !isNum(x) => defaultDomIn(STR_DEFAULT_OTHER)
    }
  }

  def domIn(strSet: Set[String])(absBool: AbsBoolUtil): AbsBool =
    strSet.foldLeft(absBool.Bot)((absB, str) => absB + (this domIn str)(absBool))

  def domIn(absStr: AbsString)(absBool: AbsBoolUtil): AbsBool = {
    absStr.gamma match {
      case ConSetCon(strSet) => (this domIn strSet)(absBool)
      case ConSetBot() => absBool.Bot
      case ConSetTop() => absStr.gammaIsAllNums match {
        case ConSingleBot() => absBool.Bot
        case ConSingleCon(true) =>
          this.map.get(STR_DEFAULT_NUMBER) match {
            case Some((numPropV, _)) if !numPropV.objval.value.isBottom => absBool.Top
            case _ if map.keySet.exists(x => !(x.take(1) == "@") && isNum(x)) => absBool.Top
            case _ => absBool.False
          }
        case ConSingleCon(false) =>
          this.map.get(STR_DEFAULT_OTHER) match {
            case Some((otherPropV, _)) if !otherPropV.objval.value.isBottom => absBool.Top
            case _ if map.keySet.exists(x => !(x.take(1) == "@") && !isNum(x)) => absBool.Top
            case _ => absBool.False
          }
        case ConSingleTop() =>
          (this.map.get(STR_DEFAULT_NUMBER), this.map.get(STR_DEFAULT_OTHER)) match {
            case (Some((numPropV, _)), _) if !numPropV.objval.value.isBottom => absBool.Top
            case (_, Some((otherPropV, _))) if !otherPropV.objval.value.isBottom => absBool.Top
            case _ if this.map.keySet.exists(x => !(x.take(1) == "@")) => absBool.Top
            case _ => absBool.False
          }
      }
    }
  }

  def collectKeysStartWith(prefix: String): Set[String] = {
    this.map.keySet.filter(s => s.startsWith(prefix))
  }
}

object Obj {
  val ObjMapBot: Map[String, (PropValue, Absent)] = HashMap[String, (PropValue, Absent)]()

  def apply(m: Map[String, (PropValue, Absent)]): Obj = new Obj(m)

  def Bot: Utils => Obj = utils => {
    val map = ObjMapBot +
      (STR_DEFAULT_NUMBER -> (PropValue.Bot(utils), AbsentBot)) +
      (STR_DEFAULT_OTHER -> (PropValue.Bot(utils), AbsentBot))

    new Obj(map)
  }

  def Empty: Utils => Obj = utils => {
    val map = ObjMapBot +
      (STR_DEFAULT_NUMBER -> (PropValue.Bot(utils), AbsentTop)) +
      (STR_DEFAULT_OTHER -> (PropValue.Bot(utils), AbsentTop))

    new Obj(map)
  }

  ////////////////////////////////////////////////////////////////
  // new Object constructos
  ////////////////////////////////////////////////////////////////
  def newObject(utils: Utils): Obj = newObject(BuiltinObject.protoLoc)(utils)

  def newObject(loc: Loc)(utils: Utils): Obj = newObject(HashSet(loc))(utils)

  def newObject(locSet: Set[Loc])(utils: Utils): Obj = {
    val absFalse = utils.absBool.False
    Empty(utils)
      .update("@class", PropValue(utils.absString.alpha("Object"))(utils))
      .update("@proto", PropValue(ObjectValue(Value(locSet)(utils), absFalse, absFalse, absFalse)))
      .update("@extensible", PropValue(utils.absBool.True)(utils))
  }

  def newArgObject(absLength: AbsNumber)(utils: Utils): Obj = {
    val protoVal = Value(BuiltinObject.protoLoc)(utils)
    val lengthVal = Value(PValue(absLength)(utils))
    val absFalse = utils.absBool.False
    val absTrue = utils.absBool.True
    Empty(utils)
      .update("@class", PropValue(utils.absString.alpha("Arguments"))(utils))
      .update("@proto", PropValue(ObjectValue(protoVal, absFalse, absFalse, absFalse)))
      .update("@extensible", PropValue(absTrue)(utils))
      .update("length", PropValue(ObjectValue(lengthVal, absTrue, absFalse, absTrue)))
  }

  def newArrayObject(absLength: AbsNumber)(utils: Utils): Obj = {
    val protoVal = Value(BuiltinArray.protoLoc)(utils)
    val lengthVal = Value(PValue(absLength)(utils))
    val absFalse = utils.absBool.False
    Empty(utils)
      .update("@class", PropValue(utils.absString.alpha("Array"))(utils))
      .update("@proto", PropValue(ObjectValue(protoVal, absFalse, absFalse, absFalse)))
      .update("@extensible", PropValue(utils.absBool.True)(utils))
      .update("length", PropValue(ObjectValue(lengthVal, utils.absBool.True, absFalse, absFalse)))
  }

  def newFunctionObject(fid: FunctionId, env: Value, l: Loc, n: AbsNumber)(utils: Utils): Obj = {
    newFunctionObject(Some(fid), Some(fid), env, Some(l), n, utils.absBool.True)(utils)
  }

  def newFunctionObject(fid: FunctionId, env: Value, l: Loc, n: AbsNumber, writable: AbsBool)(utils: Utils): Obj = {
    newFunctionObject(Some(fid), Some(fid), env, Some(l), n, writable)(utils)
  }

  def newBuiltinFunctionObject(fid: FunctionId, n: AbsNumber)(utils: Utils): Obj = {
    val scope = Value(PValue(utils.absNull.Top)(utils))
    newFunctionObject(Some(fid), None, scope, None, n, utils.absBool.True)(utils)
  }

  private def newFunctionObject(fidOpt: Option[FunctionId], constructIdOpt: Option[FunctionId], env: Value,
    locOpt: Option[Loc], n: AbsNumber, writable: AbsBool)(utils: Utils): Obj = {
    newFunctionObject(fidOpt, constructIdOpt, env,
      locOpt, writable, utils.absBool.False, utils.absBool.False, n)(utils)
  }

  private def newFunctionObject(fidOpt: Option[FunctionId], constructIdOpt: Option[FunctionId], env: Value,
    locOpt: Option[Loc], writable: AbsBool, enumerable: AbsBool, configurable: AbsBool,
    absLength: AbsNumber)(utils: Utils): Obj = {
    val protoVal = Value(BuiltinFunction.protoLoc)(utils)
    val absFalse = utils.absBool.False
    val lengthVal = Value(PValue(absLength)(utils))
    val obj1 = Empty(utils)
      .update("@class", PropValue(utils.absString.alpha("Function"))(utils))
      .update("@proto", PropValue(ObjectValue(protoVal, absFalse, absFalse, absFalse)))
      .update("@extensible", PropValue(utils.absBool.True)(utils))
      .update("@scope", PropValue(ObjectValue(env)(utils)))
      .update("length", PropValue(ObjectValue(lengthVal, absFalse, absFalse, absFalse)))

    val obj2 = fidOpt match {
      case Some(fid) => obj1.update("@function", PropValue(HashSet(fid))(utils))
      case None => obj1
    }
    val obj3 = constructIdOpt match {
      case Some(cid) => obj2.update("@construct", PropValue(HashSet(cid))(utils))
      case None => obj2
    }
    val obj4 = locOpt match {
      case Some(loc) =>
        val prototypeVal = Value(PValue.Bot(utils), HashSet(loc))
        obj3.update("@hasinstance", PropValue(utils.absNull.Top)(utils))
          .update("prototype", PropValue(ObjectValue(prototypeVal, writable, enumerable, configurable)))
      case None => obj3
    }
    obj4
  }

  def newBooleanObj(absB: AbsBool)(utils: Utils): Obj = {
    val newObj = newObject(BuiltinBoolean.protoLoc)(utils)
    newObj.update("@class", PropValue(utils.absString.alpha("Boolean"))(utils))
      .update("@primitive", PropValue(absB)(utils))
  }

  def newNumberObj(absNum: AbsNumber)(utils: Utils): Obj = {
    val newObj = newObject(BuiltinNumber.protoLoc)(utils)
    newObj.update("@class", PropValue(utils.absString.alpha("Number"))(utils))
      .update("@primitive", PropValue(absNum)(utils))
  }

  def newStringObj(absStr: AbsString)(utils: Utils): Obj = {
    val newObj = newObject(BuiltinString.protoLoc)(utils)

    val newObj2 = newObj
      .update("@class", PropValue(utils.absString.alpha("String"))(utils))
      .update("@primitive", PropValue(absStr)(utils))

    val absFalse = utils.absBool.False
    val absTrue = utils.absBool.True
    absStr.gamma match {
      case ConSetCon(strSet) =>
        strSet.foldLeft(Bot(utils))((obj, str) => {
          val length = str.length
          val newObj3 = (0 until length).foldLeft(newObj2)((tmpObj, tmpIdx) => {
            val charAbsStr = utils.absString.alpha(str.charAt(tmpIdx).toString)
            val charVal = Value(PValue(charAbsStr)(utils))
            tmpObj.update(tmpIdx.toString, PropValue(ObjectValue(charVal, absFalse, absTrue, absFalse)))
          })
          val lengthVal = Value(PValue(utils.absNumber.alpha(length))(utils))
          obj + newObj3.update("length", PropValue(ObjectValue(lengthVal, absFalse, absFalse, absFalse)))
        })
      case _ =>
        val strTopVal = Value(PValue(utils.absString.Top)(utils))
        val lengthVal = Value(PValue(absStr.length(utils.absNumber))(utils))
        newObj2
          .update(utils.absString.NumStr, PropValue(ObjectValue(strTopVal, absFalse, absTrue, absFalse)), utils)
          .update("length", PropValue(ObjectValue(lengthVal, absFalse, absFalse, absFalse)))
    }
  }

  ////////////////////////////////////////////////////////////////
  // new Scope Object constructors
  ////////////////////////////////////////////////////////////////
  def newDeclEnvRecordObj(outerEnv: Value)(utils: Utils): Obj = {
    Empty(utils).update("@outer", PropValue(ObjectValue(outerEnv)(utils)))
  }

  def newPureLocalObj(envVal: Value, thisLocSet: Set[Loc])(utils: Utils): Obj = {
    Empty(utils)
      .update("@env", PropValue(ObjectValue(envVal)(utils)))
      .update("@this", PropValue(ObjectValue(thisLocSet)(utils)))
      .update("@exception", PropValue.Bot(utils))
      .update("@exception_all", PropValue.Bot(utils))
      .update("@return", PropValue(utils.absUndef.Top)(utils))
  }
}
