  function testcase() 
  {
    var stepFiveOccurs = false;
    var obj = {
      
    };
    Object.defineProperty(obj, "length", {
      get : (function () 
      {
        return {
          valueOf : (function () 
          {
            throw new TypeError();
          })
        };
      }),
      configurable : true
    });
    var fromIndex = {
      valueOf : (function () 
      {
        stepFiveOccurs = true;
        return 0;
      })
    };
    try
{      Array.prototype.indexOf.call(obj, undefined, fromIndex);
      return false;}
    catch (e)
{      return (e instanceof TypeError) && ! stepFiveOccurs;}

  }
  {
    var __result1 = testcase();
    var __expect1 = true;
  }
  