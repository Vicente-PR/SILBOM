const test = require('node:test');
const assert = require('node:assert/strict');
const nfc = require('../../android-app/assets/nfc-code.js');

test('tres etiquetas grabadas son válidas', () => {
  for (const id of ['EQ-COM-03', 'EQ-ENV-01', 'EQ-EXT-02']) {
    assert.equal(nfc.isValidCode(id), true);
    assert.equal(nfc.machineEndpoint(id), '/maquinas/' + id);
  }
});
test('elimina solo espacios externos y conserva identificadores', () => {
  assert.equal(nfc.normalizeCode('  EQ-ENV-01\n'), 'EQ-ENV-01');
  assert.equal(nfc.normalizeCode(null), '');
});
test('rechaza textos mal formados y no transforma datos arbitrarios', () => {
  for (const value of ['enEQ-COM-03', 'EQ-COM', 'eq-com-03', 'EQ-COM-03/../', '', 'HOLA', 'EQ-COM-03 EQ-ENV-01']) {
    assert.equal(nfc.isValidCode(value), false);
    assert.throws(() => nfc.machineEndpoint(value));
  }
});
