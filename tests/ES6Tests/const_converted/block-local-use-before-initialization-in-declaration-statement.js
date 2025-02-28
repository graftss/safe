function assert(param) { return Boolean(param)}
assert.throws = function (error, func, message) { try{ func(); return false; } catch(e){ return e instanceof error;}}
// Copyright (C) 2011 the V8 project authors. All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.
/*---
es6id: 13.1
description: >
    const: block local use before initialization in declaration statement.
    (TDZ, Temporal Dead Zone)
---*/
var __result1 = assert.throws(ReferenceError, function() {
  {
    const x = x + 1;
  }
});
var __expect1 = true;
