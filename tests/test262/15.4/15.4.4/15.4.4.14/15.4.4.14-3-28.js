  function testcase() 
  {
    var targetObj = {
      
    };
    var obj = {
      0 : targetObj,
      4294967294 : targetObj,
      4294967295 : targetObj,
      length : 4294967296
    };
    return Array.prototype.indexOf.call(obj, targetObj) === - 1;
  }
  {
    var __result1 = testcase();
    var __expect1 = true;
  }
  