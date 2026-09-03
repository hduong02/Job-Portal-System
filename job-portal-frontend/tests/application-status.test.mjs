import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';

async function loadReducer() {
  const context = vm.createContext({ console });
  const toolkit = new vm.SyntheticModule(['createSlice', 'createAsyncThunk'], function () {
    this.setExport('createSlice', createSlice); this.setExport('createAsyncThunk', createAsyncThunk);
  }, { context });
  const api = new vm.SyntheticModule(['default'], function () {
    this.setExport('default', { patch: async () => ({ data: {} }) });
  }, { context });
  const modules = new Map();
  async function load(url) {
    if (modules.has(url.href)) return modules.get(url.href);
    const module = new vm.SourceTextModule(await readFile(url, 'utf8'), { context, identifier: url.href });
    modules.set(url.href, module);
    await module.link(async (specifier, parent) => {
      if (specifier === '@reduxjs/toolkit') return toolkit;
      if (specifier === '../api') return api;
      return load(new URL(specifier + '.js', parent.identifier));
    });
    return module;
  }
  const module = await load(new URL('../src/redux-store/application/applicationSlice.js', import.meta.url));
  await module.evaluate(); return module.namespace.default;
}
const reducer = await loadReducer();
const patch = { id: 1, status: 'SHORTLISTED', updatedAt: '2026-10-08T12:00:00' };
const action = { type: 'application/updateApplicationStatus/fulfilled', payload: patch };
test('compact patch preserves loaded details in matching views', () => {
  const details = { id: 1, status: 'PENDING', candidate: { fullName: 'Candidate' },
    job: { title: 'Engineer' }, company: { name: 'Company' }, notes: [{ id: 7 }] };
  const state = { ...reducer(undefined, { type: 'init' }), applications: [details],
    myApplications: [{ ...details }], currentApplication: { ...details }, isActionLoading: true };
  const result = reducer(state, action);
  for (const app of [result.applications[0], result.myApplications[0], result.currentApplication]) {
    assert.equal(app.status, 'SHORTLISTED'); assert.equal(app.updatedAt, patch.updatedAt);
    assert.equal(app.candidate.fullName, 'Candidate'); assert.equal(app.job.title, 'Engineer');
    assert.equal(app.notes[0].id, 7);
  }
  assert.equal(result.isActionLoading, false);
});
test('updating a row preserves a different open application', () => {
  const state = { ...reducer(undefined, { type: 'init' }), applications: [{ id: 1, status: 'PENDING' }],
    currentApplication: { id: 2, status: 'PENDING', job: { title: 'Other' } } };
  const result = reducer(state, action);
  assert.equal(result.currentApplication.id, 2); assert.equal(result.currentApplication.status, 'PENDING');
  assert.equal(result.currentApplication.job.title, 'Other');
});
