import { createSlice } from "@reduxjs/toolkit";
import {
  activateUser,
  deleteUser,
  fetchAllUsers,
  suspendUser,
} from "./adminThunk";

import { replaceInList } from "../utils/replaceInList";
import { emptyPage, pageResult } from "../utils/pageResult";

const initialState = {
  users: [],
  usersPage: emptyPage,
  isLoading: false,
  isActionLoading: false,
  error: null,
  actionError: null,
};

const adminUserSlice = createSlice({
  name: "adminUser",
  initialState,
  extraReducers: (builder) => {
    builder
      .addCase(fetchAllUsers.pending, (state) => {
        ((state.isLoading = true), (state.error = null));
      })
      .addCase(fetchAllUsers.fulfilled, (state, { payload }) => {
        const result = pageResult(payload);
        state.isLoading = false;
        state.users = result.content;
        state.usersPage = result.page;
      })
      .addCase(fetchAllUsers.rejected, (state, { payload }) => {
        state.isLoading = false;
        state.error = payload;
      });

    // ── suspendUser ──────────────────────────────────────────────────────────
    builder
      .addCase(suspendUser.pending, (state) => {
        state.isActionLoading = true;
        state.actionError = null;
      })
      .addCase(suspendUser.fulfilled, (state, { payload }) => {
        state.isActionLoading = false;
        replaceInList(state.users, payload);
      })
      .addCase(suspendUser.rejected, (state, { payload }) => {
        state.isActionLoading = false;
        state.actionError = payload;
      });

    builder
      .addCase(activateUser.pending, (state) => {
        state.isActionLoading = true;
        state.actionError = null;
      })
      .addCase(activateUser.fulfilled, (state, { payload }) => {
        state.isActionLoading = false;
        replaceInList(state.users, payload);
      })
      .addCase(activateUser.rejected, (state, { payload }) => {
        state.isActionLoading = false;
        state.actionError = payload;
      });

    builder
      .addCase(deleteUser.pending, (state) => {
        state.isActionLoading = true;
        state.actionError = null;
      })
      .addCase(deleteUser.fulfilled, (state, { payload }) => {
        state.isActionLoading = false;
        replaceInList(state.users, payload);
      })
      .addCase(deleteUser.rejected, (state, { payload }) => {
        state.isActionLoading = false;
        state.actionError = payload;
      });
  },
});


export default adminUserSlice.reducer
