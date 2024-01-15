/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import base32 from './base32';
import {InvalidArgumentError} from './errors';

// limit the alias to the base32 alphabet excluding padding, other checks will be done in base32.decode. We need
// the check here because base32.decode allows lower case letters, padding, and auto corrects some typos.
const accountAliasRegex = /^(\d{1,5}\.){0,2}[A-Z2-7]+$/;
const noShardRealmAccountAliasRegex = /^[A-Z2-7]+$/;

class AccountAlias {
  /**
   * Creates an AccountAlias object.
   * @param {string|null} shard
   * @param {string|null} realm
   * @param {string} base32Alias
   */
  constructor(shard, realm, base32Alias) {
    this.shard = shard;
    this.realm = realm;
    this.alias = base32.decode(base32Alias);
    this.base32Alias = base32Alias;
  }

  toString() {
    if (this.realm === null) {
      return this.base32Alias;
    }

    if (this.shard === null) {
      return `${this.realm}.${this.base32Alias}`;
    }

    return `${this.shard}.${this.realm}.${this.base32Alias}`;
  }

  /**
   * Checks if the accountAlias string is valid
   * @param {string} accountAlias
   * @param {boolean} noShardRealm If shard realm is allowed as a part of the alias.
   * @return {boolean}
   */
  static isValid(accountAlias, noShardRealm = false) {
    const regex = noShardRealm ? noShardRealmAccountAliasRegex : accountAliasRegex;
    return typeof accountAlias === 'string' && regex.test(accountAlias);
  }

  /**
   * Parses a string to an AccountAlias object.
   * @param {string} str
   * @return {AccountAlias}
   */
  static fromString(str) {
    if (!AccountAlias.isValid(str)) {
      throw new InvalidArgumentError(`Invalid accountAlias string ${str}`);
    }

    const parts = str.split('.');
    parts.unshift(...[null, null].slice(0, 3 - parts.length));

    try {
      return new AccountAlias(...parts);
    } catch (err) {
      throw new InvalidArgumentError(`Invalid accountAlias string ${str}`);
    }
  }
}

export default AccountAlias;
