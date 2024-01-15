/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import config from './config';

const TEST_RESULT_TYPES = {
  ALL: 'all', // all means to include both 'failed' and 'passed' test results
  FAILED: 'failed',
  PASSED: 'passed',
};

const currentResults = {}; // Results of current tests are stored here

/**
 * Initializer for results
 * @return {} None. Updates currentResults
 */
const initResults = () => {
  const {servers} = config;

  for (const server of servers) {
    currentResults[server.name] = {
      ...server,
      results: [],
    };
  }
};

/**
 * Saves the results from the current test run
 * @param {Object} server The server under test
 * @param {Object} results The results of the current test run
 * @return {} None. Updates currentResults
 */
const saveResults = (server, results) => {
  if (server.name) {
    currentResults[server.name] = {
      ...server,
      results,
    };
  }
};

/**
 * Gets the current results of the server
 *
 * @param {String} name server name
 * @return {Object} results object
 */
const getServerCurrentResults = (name) => {
  return currentResults[name]?.results?.testResults || [];
};

const filterTestDetails = (result, resultType = TEST_RESULT_TYPES.FAILED) => {
  if (resultType === TEST_RESULT_TYPES.ALL) {
    return result;
  }

  return {
    ...result,
    results: {
      ...result.results,
      testResults: result?.results?.testResults?.filter((r) => r.result === resultType),
    },
  };
};

/**
 * Getter for a snapshot of results
 * @param {String} resultType The resultType to filter test result details by
 * @return {Object} Snapshot of results from the latest completed round of tests
 */
const getStatus = (resultType) => {
  const results = Object.values(currentResults).map((result) => filterTestDetails(result, resultType));
  const httpErrorCodes = results
    .map((result) => result.httpCode)
    .filter((httpCode) => httpCode < 200 || httpCode > 299);
  const httpCode = httpErrorCodes.length === 0 ? 200 : 409;

  return {
    results,
    httpCode,
  };
};

/**
 * Getter for a snapshot of results for a server specified in the HTTP request
 * @param {String} name server name
 * @param {String} resultType The resultType to filter test result details by
 * @return {Object} Snapshot of results from the latest completed round of tests for the specified server
 */
const getStatusByName = (name, resultType) => {
  let ret = {
    httpCode: 400,
    results: {
      message: 'Failed',
      numFailedTests: 0,
      numPassedTests: 0,
      success: false,
      testResults: [],
    },
  };

  // Return 404 (Not found) for illegal name of the serer
  if (name === undefined || name === null) {
    ret.httpCode = 404;
    ret.results.message = `Name ${name} not found`;
    return ret;
  }

  // Return 404 (Not found) for if the server doesn't appear in our results table
  const currentResult = currentResults[name];
  if (!currentResult || !currentResult.results) {
    ret.httpCode = 404;
    ret.results.message = `Test results unavailable for server: ${name}`;
    return ret;
  }

  ret = filterTestDetails(currentResult, resultType);
  ret.httpCode = ret.results.success ? 200 : 409;
  return ret;
};

export default {
  TEST_RESULT_TYPES,
  initResults,
  saveResults,
  getServerCurrentResults,
  getStatus,
  getStatusByName,
};
