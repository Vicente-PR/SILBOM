/* Utilidades de identificación NFC, compartidas entre Android y tests Node. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.SilbomNfcCode = api;
}(typeof window === 'undefined' ? null : window, function () {
  'use strict';
  const PATTERN = /^EQ-[A-Z]{2,4}-[0-9]{2}$/;
  function normalizeCode(value) {
    return typeof value === 'string' ? value.trim() : '';
  }
  function isValidCode(code) {
    return typeof code === 'string' && PATTERN.test(code);
  }
  function machineEndpoint(code) {
    if (!isValidCode(code)) throw new Error('Código de máquina inválido');
    return '/maquinas/' + encodeURIComponent(code);
  }
  return Object.freeze({normalizeCode, isValidCode, machineEndpoint});
}));
