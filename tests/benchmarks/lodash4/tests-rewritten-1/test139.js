QUnit.module('lodash.lowerFirst');
(function () {
    QUnit.test('should lowercase only the first character', function (assert) {
        assert.expect(3);
        assert.strictEqual(_.lowerFirst('fred'), __str_top__);
        assert.strictEqual(_.lowerFirst('Fred'), 'fred');
        assert.strictEqual(_.lowerFirst('FRED'), 'fRED');
    });
}());