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

import config from '../config';
import {requestStartTime, responseContentType, responseDataLabel} from '../constants';
import {NotFoundError} from '../errors';
import {JSONStringify} from '../utils';

const {
  response: {headers},
} = config;

const CONTENT_TYPE_HEADER = 'Content-Type';
const APPLICATION_JSON = 'application/json; charset=utf-8';
const LINK_NEXT_HEADER = 'Link';
const linkNextHeaderValue = (linksNext) => `<${linksNext}>; rel="next"`

// Response middleware that pulls response data passed through request and sets in response.
// Next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const responseHandler = async (req, res, next) => {
  const responseData = res.locals[responseDataLabel];
  if (responseData === undefined) {
    // unmatched route will have no response data, pass NotFoundError to next middleware
    throw new NotFoundError();
  } else {
    res.set(headers.default);
    res.set(headers.path[req.route.path]);

    const code = res.locals.statusCode;
    const contentType = res.locals[responseContentType] || APPLICATION_JSON;
    const linksNext = res.locals.responseData.links?.next;
    res.status(code);
    res.set(CONTENT_TYPE_HEADER, contentType);

    if (linksNext) {
      res.set(LINK_NEXT_HEADER, linkNextHeaderValue(linksNext));
    }

    if (contentType === APPLICATION_JSON) {
      res.send(JSONStringify(responseData));
    } else {
      res.send(responseData);
    }

    const startTime = res.locals[requestStartTime];
    const elapsed = startTime ? Date.now() - startTime : 0;
    logger.info(`${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${code}`);
  }
};

export default responseHandler;
