var fs = require("fs");
var handlebars = require("./handlebars.js");

// read the template file
var templateFile = process.argv[process.argv.length - 2];
var template = fs.readFileSync(templateFile, "utf-8");

// precompile the template for AMD
var precompiledTemplate = handlebars.precompile(template);
precompiledTemplate = "define(['handlebars'], function(Handlebars) {\nreturn Handlebars.template(" + precompiledTemplate + ")\n});";

// output the precompiled JavaScript
var javaScriptFile = process.argv[process.argv.length - 1];
fs.writeFileSync(javaScriptFile, precompiledTemplate, "utf-8");
