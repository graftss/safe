QUnit.module('lodash(...).push');
(function () {
    QUnit.test('should append elements to `array`', function (assert) {
        assert.expect(2);
        if (!isNpm) {
            var array = [__num_top__], wrapped = _(array).push(__num_top__, __num_top__), actual = wrapped.value();
            assert.strictEqual(actual, array);
            assert.deepEqual(actual, [
                __num_top__,
                __num_top__,
                __num_top__
            ]);
        } else {
            skipAssert(assert, 2);
        }
    });
    QUnit.test('should accept falsey arguments', function (assert) {
        assert.expect(1);
        if (!isNpm) {
            var expected = lodashStable.map(falsey, stubTrue);
            var actual = lodashStable.map(falsey, function (value, index) {
                try {
                    var result = index ? _(value).push(__num_top__).value() : _().push(__num_top__).value();
                    return lodashStable.eq(result, value);
                } catch (e) {
                }
            });
            assert.deepEqual(actual, expected);
        } else {
            skipAssert(assert);
        }
    });
}());