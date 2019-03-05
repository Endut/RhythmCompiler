InputStream {
	var <>pos = 0, routine, inputString;
	
	*new { arg input;
		^super.new.init(input)
	}
	init { arg input;
		inputString = input;
		routine = Routine {
			input.do(_.yield)
		}
		^this
	}
	next {
		var ch = routine.next;
		pos = pos + 1;
		^ch;
	}
	peek {
		var char = inputString[pos];
		^""++char;
	}
	eof {
		^(inputString[pos] == nil)

	}
	croak { arg msg;
		Error(format("RhythmCompiler error: % (pos: %).", msg, pos)).throw;
	}
}

TokenStream {
	// turns an input string into a stream of tokens which the Parser consumes
	var current;
	var alpha_chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	var punctuation = "[]";
	var binop = "%!*/|?";
	var unop = ":";
	var digit = "0123456789";
	var input;

	*new { arg inputString;
		^super.new.init(inputString)
	}

	init { arg inputString;
		current = ();
		input = InputStream(inputString);
		^this
	}

	// consume stream while predicate is true
	// predicate is function that returns bool eg if char is whitespace
	readWhile { arg predicate;
		var str = "";
		while ({input.eof.not && predicate.value(input.peek)}, {
			str = str ++ input.next;
			});
		^str;
	}

	readNote {
		var noteName = input.peek, tail, value;
		input.next;
		tail = this.readWhile({ arg ch; ch == "-" });
		value = noteName ++ tail;
		^(type: 'note', val: noteName++value.size)
	}

	readRest {
		var rest = this.readWhile({ arg ch; ch == "-" });
		^(type: 'rest', val: rest.size);
	}

	readPunctuation {
		^(type: 'punc', val: input.next)
	}

	readBinOp {
		^(type: 'binop', val: input.next, left: nil, right: nil)
	}

	readUnOp {
		^(type: 'unop', val: input.next, left: nil)
	}

	readNumber {
		var numberString = this.readWhile({ arg ch; (digit++".").contains(ch)});

		^(type: 'num', val: numberString.asFloat)
	}

	parseChar { arg ch;
		var res = case
			{ alpha_chars.contains(ch) } { this.readNote }
			{ ch == "-" } { this.readRest }
			{ punctuation.contains(ch) } { this.readPunctuation }
			{ binop.contains(ch) } { this.readBinOp }
			{ digit.contains(ch) } { this.readNumber }
			{ unop.contains(ch) } { this.readUnOp };

		res ?? { this.croak(format("unrecognized character %", ch)) }
		^res;
	}

	readNext {
		var ch;
		this.readWhile({ arg c; " \t\n".contains(c) });
		ch = input.peek;
		if(input.eof) {
			^nil
		} {
			^this.parseChar(ch)
		}
	}

	next {
		current = this.readNext;
		^current
	}

	peek {
		^current
	}

	eof {
		^(this.peek == nil)
	}

	collectRemaining { arg fn = { arg item; item };
		var arr = [];
		while({this.eof.not}, {
			var item = this.peek;
			if (item != ()) {
				arr = arr.add(fn.value(this.peek));	
			};
			this.next;
			});
		^arr
	}

	croak { arg msg;
		input.croak(msg);
	}
}


Parser {
	// generates AST from token stream
	var input;
	var <>tree;
	var continue;

	*new { arg inputString;
		^super.new.init(inputString);
	}

	*fromTokenStream { arg inputStream;
		^super.new.initFromTokenStream(inputStream);
	}

	init { arg inputString;
		input = TokenStream(inputString);
		tree = (type: 'tree', val: []);
		continue = true;
		^this.fillTree;
	}

	initFromTokenStream { arg inputStream, parentNode;
		input = inputStream;
		tree = (type: 'tree', val: []);
		continue = true;
		^this.fillTree;
	}

	fillTree {
		while({ input.eof.not && this.continueNode }, {
			var token = input.peek;
			input.next;
			if (token != ()) {
				this.parseToken(token);	
			};
		})
	}

	parseToken { arg token;
		switch(token.type,
			'rest', { this.parseRest(token) },
			'note', { this.parseNote(token) },
			'punc', { this.parsePunc(token) },
			'binop', { this.parseBinOp(token) },
			'unop', { this.parseUnOp(token) },
 			'num', { this.parseNum(token) }
		);
	}

	parseRest { arg token;
		this.addNode(token);
	}

	parseNote { arg token;
		this.addNode(token);
	}

	parsePunc { arg token;
		var val = "" ++ token.val;
		switch(val, 
			"[", {
				var newNode = Parser.fromTokenStream(input).tree;
				this.addNode(newNode);
				}, 
			"]", { continue = false }
			);
	}

	parseBinOp { arg token;
		var left, right;
		var lastIndex = tree.val.size - 1;
		
		right = input.peek;
		left = tree.val[lastIndex];
		
		// handle errors binop errors
		case
			{ right.notNil && right.type != 'num' } {
				input.croak(format("right side of % must be a number", token.val))
				}
			{ right.isNil } {
				input.croak(format("% is a binary operator, must have a right side", token.val))
			};
		
		token.right = right;
		if ( (left.type == 'note') || (left.type == 'rest' ) ) {
			var tr;
			left = tree;
			token.left = left;
			tr = (type: 'tree', val: [token]);
			tree = tr;
		} {
			token.left = left;
			tree.val[lastIndex] = token;
		};
		
	}

	parseUnOp { arg token;
		var left;
		var lastIndex = tree.val.size - 1;
		left = tree.val[lastIndex];

		if ( (left.type == 'note') || (left.type == 'rest') ) {
			var tr;
			left = tree;
			token.left = left;
			tr = (type: 'tree', val: [token]);
			tree = tr;
		} {
			token.left = left;
			tree.val[lastIndex] = token;
		};
	}

	parseNum { arg token;
		// this.addNode(token);
	}

	addNode { arg node;
		tree.val = tree.val.add(node);
	}
	continueNode {
		^continue
	}

}

Compile {
	var <result;
	// returns a routine from input string 
	*new { arg inputString, repeats = inf, fn;
		^super.new.init(inputString, repeats, fn)
	}

	init { arg inputString, repeats, fn;
		result = this.processNode(Parser(inputString).tree).flat;
		^this.asStream(repeats, fn)
	}

	embedNode { arg node, fn;
		node.putAll(fn.value(node.val));
		node.value.embedInStream;
	}

	processNode { arg node; 
		var res = switch(node.type,
			'binop', { this.processBinOp(node) },
			'unop', { this.processUnOp(node) },
			'num', { this.processNum(node) },
			'tree', { this.processTree(node) },
			'note', { this.processNote(node) },
			'rest', { this.processRest(node) }
			);
		^res;
	}

	processTree { arg tree;
		^tree.val.collect({ arg item, i;
			this.processNode(item);
			})
	}

	processRest { arg rest;
		^(dur: rest.val, val: nil)
	
	}
	
	processNote { arg note;
		var name = note.val[0].asSymbol;
		var dur = "" ++ note.val[1..]; 
		// ^(dur: dur.asFloat, val: name)
		^(dur: dur.asFloat, val: name)
	}

	processNum { arg num;
		^num.val
	}

	processBinOp { arg binop;
		var left = Array.newFrom(this.processNode(binop.left)).flat;
		var right = this.processNum(binop.right);
		var val = "" ++ binop.val;
		var res;
		res = switch(val, 
			"%", { this.padTo(left, right)},
			"!", { this.duplicate(left, right)},
			"*", { this.multiply(left, right)},
			"/", { this.divide(left, right)},
			"|", { this.truncate(left, right)}
			);
		^res
	}

	padTo { arg left, right;
		var total = left.collect(_.dur).sum;
		var remainder = total % right;
		left = left.add((dur: right - remainder, val: nil));
		^left
	}

	truncate { arg left, right;
		var total = left.collect(_.dur).sum;
		var remainder = total % right;
		var index = left.size - 1;
		var item;

		if (total <= right) {
			left = left.add((dur: right - remainder, val: nil))			
		} {
			while( { (remainder > 0) && (index > 0) }, {
				item = left[index];
				if ( remainder < item.dur ) {
						item.dur = item.dur - remainder;
						remainder = 0;
					} { remainder >= item.dur } {
						left.removeAt(index);
						remainder = remainder - item.dur;
					}; 
				index = index - 1
			});			
		};
		^left
	}

	duplicate { arg left, right;
		^left.dup(right)
	}

	multiply { arg left, right;
		^left.collect({ arg item; item.dur = item.dur * right });
	}

	divide { arg left, right;
		if (right == 0) {
			^Error("division by zero error").throw
		} {
			^left.collect({ arg item; item.dur = item.dur / right });	
		}
	}

	processUnOp { arg unop;
		var left = Array.newFrom(this.processNode(unop.left)).flat;
		var val = "" ++ unop.val;
		var res;

		res = switch(val,
			":", { this.scramble(left) });
		^res
	}

	scramble { arg left;
		^{
			Routine {
				left.scramble.do({ arg node;
					this.embedNode(node)
				});
			}
		}
	}

	asStream { arg repeats = inf, fn;
		^Routine {
			repeats.do {
				result.do({ arg node;
					this.embedNode(node, fn);
				});
			}
		}
	}
}
