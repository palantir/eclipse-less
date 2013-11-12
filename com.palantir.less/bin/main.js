var fs = require("fs");
var less = require("less");

// read the less file
var lessFile = process.argv[process.argv.length - 2];
var lessContents = fs.readFileSync(lessFile, "utf-8");

// compile
var parser = new less.Parser({
	filename: lessFile
});

parser.parse(lessContents, function (e, tree) {
	var cssContents = tree.toCSS();
	var cssFile = process.argv[process.argv.length - 1];
	fs.writeFileSync(cssFile, cssContents, "utf-8");
});
