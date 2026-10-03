#!/bin/sh
cd "$(dirname "$0")" || exit 1
exec java -cp 'QuizPlatform.jar:lib/*' quiz.Main
