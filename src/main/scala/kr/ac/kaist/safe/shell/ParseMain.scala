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

package kr.ac.kaist.safe.shell

import java.io.{ FileNotFoundException, IOException, BufferedWriter, FileWriter }
import kr.ac.kaist.safe.Config
import kr.ac.kaist.safe.compiler.Parser
import kr.ac.kaist.safe.errors.UserError
import kr.ac.kaist.safe.nodes.Program
import kr.ac.kaist.safe.safe_util.NodeFactory

////////////////////////////////////////////////////////////////////////////////
// Parse
////////////////////////////////////////////////////////////////////////////////
object ParseMain {
  val return_code = 0
  /**
   * Parses files. If they parse ok, it will say "Ok".
   * If you want a dump then give -out=outfile.
   */
  def parse(config: Config): Int = {
    if (config.FileNames.length == 0) throw new UserError("Need a file to parse")
    try {
      val pgm: Program = Parser.fileToAST(config.FileNames)
    } catch {
      case f: FileNotFoundException => throw new UserError(f + " not found")
      case e: Exception => println(e.getCause)
    }
    return_code
  }
}
