import assert from 'node:assert/strict';
import { execFile, execFileSync } from 'node:child_process';
import { existsSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

// Build :app:assembleMemberIntegrationEmulatorAndroidTest with -PmemberIntegrationEmulatorTests=true.
// Run this script via Firebase emulators:exec --only auth,database --project demo-kgyms-member-integration
// with firebase.member-integration.json and an offline Android Emulator selected in ANDROID_SERIAL.
const project = 'demo-kgyms-member-integration';
const host = '127.0.0.1';
const dbPort = 9226;
const authPort = 9296;
assert.equal(process.env.GCLOUD_PROJECT, project);
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, host + ':' + dbPort);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, host + ':' + authPort);

const serial = process.env.ANDROID_SERIAL ?? '';
assert.match(serial, /^emulator-\d+$/, 'Select a dedicated, offline Android Emulator via ANDROID_SERIAL');
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
assert.ok(sdk, 'Set ANDROID_HOME to the local Android SDK');
const exe = process.platform === 'win32' ? '.exe' : '';
const adb = path.join(sdk, 'platform-tools', 'adb' + exe);
const call = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', windowsHide: true, timeout: 20_000 });
const run = promisify(execFile);

function requireOfflineEmulator() {
  assert.equal(call('shell', 'settings', 'get', 'global', 'airplane_mode_on').trim(), '1');
  assert.match(call('shell', 'dumpsys', 'connectivity'), /Active default network: none\b/);
  for (const ip of ['-4', '-6']) {
    const routes = call('shell', 'ip', ip, 'route', 'show', 'table', 'all').trim().split(/\r?\n/).filter(Boolean);
    assert.ok(routes.length > 0, 'Could not verify the emulator route table');
    for (const route of routes) {
      assert.match(route, /^(?:(?:local|broadcast)\s.*\btable local\b|unreachable\s)/, 'External network route detected');
    }
  }
}

const appId = 'com.kgyms.app.memberintegrationemu';
const runner = 'com.kgyms.app.integration.MemberEmulatorRunner';
const root = fileURLToPath(new URL('../', import.meta.url));
const apkDir = path.join(root, 'app/build/outputs/apk');
const appApk = path.join(apkDir, 'memberIntegrationEmulator/app-memberIntegrationEmulator.apk');
const testApk = path.join(apkDir, 'androidTest/memberIntegrationEmulator/app-memberIntegrationEmulator-androidTest.apk');
const bin = readdirSync(path.join(sdk, 'build-tools')).sort().reverse()
  .map(version => path.join(sdk, 'build-tools', version, 'aapt2' + exe)).find(existsSync);
assert.ok(bin, 'Android build-tools with aapt2 are required');
const dump = (...args) => execFileSync(bin, ['dump', ...args], { encoding: 'utf8', windowsHide: true, maxBuffer: 32 * 1024 * 1024 });
for (const [apk, id] of [[appApk, appId], [testApk, appId + '.test']]) {
  assert.ok(dump('badging', apk).startsWith("package: name='" + id + "'"));
  const manifest = dump('xmltree', apk, '--file', 'AndroidManifest.xml');
  assert.doesNotMatch(manifest, /FirebaseInitProvider|com\.kgyms\.app\.MainActivity/);
  const resources = dump('resources', apk);
  assert.ok(!/string\/(google_app_id|google_api_key|firebase_database_url|project_id)(\s|:)/.test(resources),
    'Google Services resources must be absent');
  assert.ok(!/\.apps\.googleusercontent\.com/.test(resources), 'Real Google sign-in configuration must be absent');
  if (apk === appApk) assert.ok(resources.includes('DEMO_UNUSED_NO_GOOGLE_SIGN_IN'));
  if (apk === testApk) assert.ok(manifest.includes(runner));
}

const targets = {
  AppIntegrationTest: ['integration', [
    'signUpKeepsDraftAcrossAllBackStepsAndRestorationThenEntersMember',
    'memberLogoutClearsGraphAndANewPreviewHasFreshProfileState',
    'noMockSuccessWhenDebugModeIsDisabledAndForgotPasswordIsExplicitPreview',
    'googleButtonIsOnlyAMockAndSignUpStillGoesThroughGender'
  ]],
  MemberAvatarIntegrationTest: ['ui.member', ['sharedBitmapSurvivesMemberTabsAndProfileEditUpdatesFallback']],
  MemberDashboardUiTest: ['ui.member.home', ['compactShellKeepsFiveDistinctTapTargetsAndSelectedTabs']],
  MemberHomeIntegrationTest: ['ui.member.home', ['avatarProfileBackAndScrollStateSurviveTabChangesAndRecreation']],
  MemberPreferencesIntegrationTest: ['ui.member.home', [
    'qrOptionsOpenTheirScreensAndBackReturnsHome',
    'savedFitnessTargetImmediatelyChangesHomeProgressAndSurvivesRestoration',
    'profileAppearanceControlsAllMemberTabsAndPersistsTheChoice'
  ]],
  ProfileFlowTest: ['ui.member.profile', [
    'settingsValidatePasswordsAndApplyLightThemeAndEnglishLocally',
    'membershipGoalsFaqAndContactHaveWorkingReturnPaths',
    'editedProfileAndSwitchesSurviveBackAndRecreation'
  ]],
  MemberWorkoutTest: ['ui.member.workout', ['recommendationTrackingSurvivesTabsAndRecreation']]
};

function summarize(selector, raw, error) {
  const cases = [];
  for (const match of raw.matchAll(/([\s\S]*?)INSTRUMENTATION_STATUS_CODE: (-?\d+)/g)) {
    const [, block, code] = match;
    if ([1, 2].includes(Number(code))) continue;
    const field = name => block.match(new RegExp('INSTRUMENTATION_STATUS: ' + name + '=([^\\r\\n]+)'))?.[1];
    const failure = field('stack');
    const cause = block.match(/Caused by: ([^\r\n]+)/)?.[1];
    const frames = block.split(/\r?\n/).map(line => line.trim())
      .filter(line => line.startsWith('at com.kgyms.app.') && /\.kt:\d+\)/.test(line)).slice(0, 3);
    cases.push({
      test: field('test'), code: Number(code), frames,
      failure: failure && !/password|token|bearer|secret|api.?key/i.test(failure) ? failure.slice(0, 280) : undefined,
      cause: cause && !/password|token|bearer|secret|api.?key/i.test(cause) ? cause.slice(0, 280) : undefined
    });
  }
  const status = raw.match(/INSTRUMENTATION_(?:RESULT: shortMsg|FAILED)[:=]([^\r\n]+)/)?.[1]?.trim();
  return {
    selector, error: error?.code, timedOut: error?.killed === true && error?.signal === 'SIGTERM',
    aborted: !cases.length || !raw.includes('INSTRUMENTATION_CODE: -1'), status, cases
  };
}

const args = process.argv.slice(2);
const selectors = args.length ? args : Object.entries(targets).flatMap(([name, [pkg, methods]]) =>
  methods.map(method => 'com.kgyms.app.' + pkg + '.' + name + '#' + method));
assert.ok(selectors.every(selector => /^com\.kgyms\.app\.[A-Za-z0-9_.]+(?:#[A-Za-z0-9_]+)?$/.test(selector)));
const reports = [];
const tunnels = [];
try {
  requireOfflineEmulator();
  assert.equal(call('reverse', '--list').trim(), '', 'Remove unrelated adb reverse tunnels from this dedicated emulator');
  for (const port of [authPort, dbPort]) {
    call('reverse', '--no-rebind', 'tcp:' + port, 'tcp:' + port);
    tunnels.push(port);
  }
  for (const apk of [appApk, testApk]) assert.match(call('install', '-r', '-t', apk), /Success/);
  // The QR navigation test asserts the Compose screen behind Android's camera dialog.
  call('shell', 'pm', 'grant', appId, 'android.permission.CAMERA');
  call('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP');
  call('shell', 'wm', 'dismiss-keyguard');
  assert.match(call('shell', 'pm', 'list', 'instrumentation'), new RegExp(appId + '\\.test/' + runner));

  for (const selector of selectors) {
    requireOfflineEmulator();
    let result;
    let error;
    try {
      result = await run(adb, ['-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        selector, appId + '.test/' + runner], { encoding: 'utf8', windowsHide: true, timeout: 240_000, maxBuffer: 16 * 1024 * 1024 });
    } catch (failure) {
      result = failure;
      error = failure;
    }
    const report = summarize(selector, (result.stdout ?? '') + '\n' + (result.stderr ?? ''), error);
    reports.push(report);
    if (process.env.KGYMS_MEMBER_TEST_REPORT) writeFileSync(process.env.KGYMS_MEMBER_TEST_REPORT, JSON.stringify(reports, null, 2));
    console.log(JSON.stringify(report));
  }
  requireOfflineEmulator();
  process.exitCode = reports.some(r => r.aborted || r.cases.some(c => c.code < 0)) ? 1 : 0;
} finally {
  for (const port of tunnels) call('reverse', '--remove', 'tcp:' + port);
}
