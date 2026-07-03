@echo off
rem Check and install this repo's agent skills for whichever CLI you have (Claude and/or Codex).
rem backend has no npm, so this is the equivalent of `npm run skills` in the Node repos.
rem   skills                 install any missing enabled plugins
rem   skills --force-update  also update everything to the latest version now
rem   skills --check         report what's missing, install nothing
node "%~dp0.claude\hooks\ensure-plugins.mjs" %*
