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

////////////////////////////////////////////////////////////////////////////////
// concrete null type
////////////////////////////////////////////////////////////////////////////////
sealed abstract class Null extends PValue
case object Null extends Null

////////////////////////////////////////////////////////////////////////////////
// null abstract domain
////////////////////////////////////////////////////////////////////////////////
trait AbsNull extends AbsDomain[Null, AbsNull] {
  def gamma: ConSimple[Null]
  def ===(that: AbsNull): AbsBool
}

trait AbsNullUtil extends AbsDomainUtil[Null, AbsNull] {
  def alpha(value: Null): AbsNull = Top
}
