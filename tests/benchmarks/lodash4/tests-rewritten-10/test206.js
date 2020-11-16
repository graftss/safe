QUnit.module('lodash.repeat');
(function () {
    var string = __str_top__;
    QUnit.test('should repeat a string `n` times', function (assert) {
        assert.expect(2);
        assert.strictEqual(_.repeat('*', 3), '***');
        assert.strictEqual(_.repeat(string, 2), 'abcabc');
    });
    QUnit.test('should treat falsey `n` values, except `undefined`, as `0`', function (assert) {
        assert.expect(1);
        var expected = lodashStable.map(falsey, function (value) {
            return value === undefined ? string : '';
        });
        var actual = lodashStable.map(falsey, function (n, index) {
            return index ? _.repeat(string, n) : _.repeat(string);
        });
        assert.deepEqual(actual, expected);
    });
    QUnit.test('should return an empty string if `n` is <= `0`', function (assert) {
        assert.expect(2);
        assert.strictEqual(_.repeat(string, 0), '');
        assert.strictEqual(_.repeat(string, -__num_top__), '');
    });
    QUnit.test('should coerce `n` to an integer', function (assert) {
        assert.expect(3);
        assert.strictEqual(_.repeat(string, __str_top__), 'abcabc');
        assert.strictEqual(_.repeat(string, 2.6), 'abcabc');
        assert.strictEqual(_.repeat('*', { 'valueOf': stubThree }), __str_top__);
    });
    QUnit.test('should coerce `string` to a string', function (assert) {
        assert.expect(2);
        assert.strictEqual(_.repeat(Object(string), 2), 'abcabc');
        assert.strictEqual(_.repeat({ 'toString': lodashStable.constant(__str_top__) }, 3), __str_top__);
    });
    QUnit.test('should work as an iteratee for methods like `_.map`', function (assert) {
        assert.expect(1);
        var actual = lodashStable.map([
            'a',
            __str_top__,
            'c'
        ], _.repeat);
        assert.deepEqual(actual, [
            __str_top__,
            __str_top__,
            __str_top__
        ]);
    });
}());