#!/usr/bin/env node
// Cross-platform dispatcher behind `npm run dev:all` / `npm run dev:stop`:
// picks start-dev.ps1/stop-dev.ps1 on Windows, start-dev.sh/stop-dev.sh
// otherwise, and inherits stdio so output/exit code pass straight through.
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = join(__dirname, '..')
const stop = process.argv.includes('--stop')
const demo = process.argv.includes('--demo')

const isWindows = process.platform === 'win32'
const script = isWindows
  ? join(repoRoot, stop ? 'stop-dev.ps1' : 'start-dev.ps1')
  : join(repoRoot, stop ? 'stop-dev.sh' : 'start-dev.sh')

const command = isWindows ? 'powershell' : 'bash'
const args = isWindows
  ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script]
  : [script]
if (demo && !stop) args.push(isWindows ? '-Demo' : '--demo')

const child = spawn(command, args, { stdio: 'inherit', cwd: repoRoot })
child.on('exit', (code) => process.exit(code === null ? 1 : code))
child.on('error', (err) => {
  console.error(`Failed to launch ${command}: ${err.message}`)
  process.exit(1)
})
