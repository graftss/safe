// XXX
//  function testcase() 
//  {
//    var arr = {
//      
//    };
//    Object.defineProperty(arr, "length", {
//      get : (function () 
//      {
//        arr[2] = "length";
//        return 3;
//      }),
//      configurable : true
//    });
//    return 2 === Array.prototype.indexOf.call(arr, "length");
//  }
//  {
//    var __result1 = testcase();
//    var __expect1 = true;
//  }
//  
