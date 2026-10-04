#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');

const windowsDir = path.join(os.homedir(), '.config', 'vscode-mcp', 'registry', 'windows');
const cwd = process.cwd();

function getActiveServers() {
  if (!fs.existsSync(windowsDir)) return [];
  try {
    const files = fs.readdirSync(windowsDir).filter(f => f.endsWith('.json'));
    const results = [];
    for (const f of files) {
      try {
        const fullPath = path.join(windowsDir, f);
        const data = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
        if (data && data.mcp && (data.mcp.portFilePath || data.mcp.port)) {
          results.push(data);
        }
      } catch (_) {}
    }
    return results;
  } catch (_) {
    return [];
  }
}

function resolveServer() {
  const servers = getActiveServers();
  if (servers.length === 0) return null;

  // 1. Exact match on workspaceRoot or workspaceFolder
  for (const s of servers) {
    const root = s.workspaceRoot || s.workspaceFolder;
    if (root && (cwd === root || cwd.startsWith(root + path.sep))) {
      return s;
    }
  }

  // 2. If only one server is registered, use it
  if (servers.length === 1) {
    return servers[0];
  }

  return null;
}

async function main() {
  let server = resolveServer();

  // Retry briefly (up to 3 seconds) in case VS Code is currently activating
  if (!server) {
    const start = Date.now();
    while (Date.now() - start < 3000) {
      await new Promise(r => setTimeout(r, 300));
      server = resolveServer();
      if (server) break;
    }
  }

  if (!server) {
    console.error(`[Calva MCP Launcher] No active Calva Backseat Driver found for workspace: ${cwd}`);
    console.error(`[Calva MCP Launcher] If VS Code is open:`);
    console.error(`  - Run "Calva: Start Backseat Driver MCP Server" in Command Palette`);
    console.error(`  - Or enable "calva-backseat-driver.autoStartMCPServer": true in VS Code settings.`);
    console.error(`[Calva MCP Launcher] If you are using Emacs, Calva MCP is offline; use clojure-mcp instead.`);
    process.exit(1);
  }

  const portArg = server.mcp.portFilePath || String(server.mcp.port);
  const wrapper = server.mcp.wrapperPath || path.join(os.homedir(), '.config', 'calva', 'backseat-driver', 'calva-mcp-server.js');
  const host = server.mcp.host || '127.0.0.1';

  if (!fs.existsSync(wrapper)) {
    console.error(`[Calva MCP Launcher] Calva wrapper script not found at ${wrapper}`);
    process.exit(1);
  }

  const child = spawn(process.execPath, [wrapper, portArg, host], {
    stdio: 'inherit'
  });

  child.on('error', (err) => {
    console.error(`[Calva MCP Launcher] Failed to spawn wrapper:`, err);
    process.exit(1);
  });

  child.on('exit', (code, signal) => {
    if (signal) {
      process.kill(process.pid, signal);
    } else {
      process.exit(code ?? 0);
    }
  });
}

main().catch(err => {
  console.error('[Calva MCP Launcher] Unexpected error:', err);
  process.exit(1);
});
