/*
 * ****************************************************************************
 * Copyright (c) 2016-2017, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ***************************************************************************
 */

package kr.ac.kaist.safe.web

import kr.ac.kaist.safe.web.utils.JsonImplicits._

case class Request(cmd: String)
case class Response(prompt: String, iter: Int, output: String, state: String, done: Boolean = false)

