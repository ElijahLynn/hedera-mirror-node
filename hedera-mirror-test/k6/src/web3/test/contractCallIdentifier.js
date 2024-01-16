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

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const data = '0x7998a1c4';

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallIdentifier') // use unique scenario name among all tests
  .data(data)
  .to(contract)
  .build();

export {options, run};
