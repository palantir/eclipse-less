/*
 * Copyright 2013 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var fs = require("fs");
var less = require("less");

// read the less file
var lessFile = process.argv[process.argv.length - 2];
var lessContents = fs.readFileSync(lessFile, "utf-8");

var parser = new less.Parser({
	filename: lessFile
});

// compile the LESS file to CSS
parser.parse(lessContents, function (e, tree) {
	var cssContents = tree.toCSS();
	var cssFile = process.argv[process.argv.length - 1];
	fs.writeFileSync(cssFile, cssContents, "utf-8");
});
