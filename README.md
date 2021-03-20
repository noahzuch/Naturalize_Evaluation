Naturalize
===============
 This repository contains some modifications to the Naturalize tool https://github.com/mast-group/naturalize that include 1. the JM smoothing technique and 2. an obfuscation logic to remove type names from the source files.
 
 This repository contains the code for the evaluation done in the seminar paper https://github.com/noahzuch/Naturalize.
 
 The following evaluations can be run:
 1. Reevaluation:
`LeaveOnOutEvaluation <outputdir-of-csv-file> codemining.java.tokenizers.JavaTokenizer all StandardScopeExtractor renaming.renamers.BaseIdentifierRenamings <list-of-project-src-dirs>`
 3. JM smoothing:
`LeaveOnOutEvaluation <outputdir-of-csv-file> codemining.java.tokenizers.JavaTokenizer all StandardScopeExtractor renaming.renamers.BaseIdentifierRenamings <list-of-project-src-dirs>`
And modify the `Default.properties`file to include the line `BaseIdentifierRenamings.ngramSmootherClass codemining.lm.ngram.smoothing.JMSmoother
`
 4. Type obfuscation:
`LeaveOnOutEvaluation <outputdir-of-csv-file> codemining.java.tokenizers.JavaObfuscatedTokenizer all ObfuscatedScopeExtractor renaming.renamers.BaseIdentifierRenamings <list-of-project-src-dirs>`
