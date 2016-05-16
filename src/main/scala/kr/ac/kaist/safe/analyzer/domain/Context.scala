/**
 * *****************************************************************************
 * Copyright (c) 2012-2013, S-Core, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ****************************************************************************
 */

package kr.ac.kaist.safe.analyzer.domain

import kr.ac.kaist.safe.util.AddressManager

case class Context(private val env: Set[Loc], private val thisBinding: Set[Loc], mayOld: Set[Address], mustOld: Set[Address]) {
  /* partial order */
  def <=(that: Context): Boolean = {
    if (this eq that) true
    else {
      this.mayOld.subsetOf(that.mayOld) &&
        (if (this.mustOld == null) true
        else if (that.mustOld == null) false
        else that.mustOld.subsetOf(this.mustOld))
    }
  }

  /* not a partial order */
  def </(that: Context): Boolean = !(this <= that)

  /* bottom checking */
  def isBottom: Boolean = {
    this.mustOld == null && this.mayOld.isEmpty
  }

  /* join */
  def +(that: Context): Context = {
    if (this eq that) this
    else if (this.isBottom) that
    else if (that.isBottom) this
    else {
      val newMustOld =
        if (this.mustOld == null) that.mustOld
        else if (that.mustOld == null) this.mustOld
        else this.mustOld.intersect(that.mustOld)

      Context(Set[Loc](), Set[Loc](), this.mayOld ++ that.mayOld, newMustOld)
    }
  }

  /* meet */
  def <>(that: Context): Context = {
    if (this eq that) this
    else {
      val newMustOld =
        if (this.mustOld == null) null
        else if (that.mustOld == null) null
        else this.mustOld ++ that.mustOld

      Context(Set[Loc](), Set[Loc](), this.mayOld.intersect(that.mayOld), newMustOld)
    }
  }

  /* substitute locR by locO */
  def subsLoc(locR: Loc, locO: Loc, addressManager: AddressManager): Context = {
    Context(Set[Loc](), Set[Loc](), mayOld + addressManager.locToAddr(locR), mustOld + addressManager.locToAddr(locR))
  }

  /* weakly substitute locR by locO, that is keep locR together */
  def weakSubsLoc(locR: Loc, locO: Loc, addressManager: AddressManager): Context = {
    Context(Set[Loc](), Set[Loc](), mayOld + addressManager.locToAddr(locR), mustOld)
  }
}