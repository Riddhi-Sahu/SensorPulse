const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write sensor_readings or camera_captures", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sensor_readings").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("camera_captures").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sensor_readings").doc("r_1").set({
      userId: ALICE_UID,
      sensorId: "hw_1",
      sensorName: "Accelerometer",
      sensorCategory: "MOTION",
      values: [0.1, 9.8, 0.2],
      unit: "m/s^2",
      accuracy: 3,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid sensor_reading create, query, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("sensor_readings");

  await assertSucceeds(
    col.doc("reading_001").set({
      userId: ALICE_UID,
      sensorId: "hw_accel_1",
      sensorName: "3-Axis Accelerometer",
      sensorCategory: "MOTION",
      values: [0.12, 9.79, -0.04],
      unit: "m/s^2",
      accuracy: 3,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Ordered query matching TelemetryRepository.observeSensorReadings
  await assertSucceeds(col.orderBy("createdAt", "desc").get());

  // Partial update on mutable notes + updatedAt only
  await assertSucceeds(
    col.doc("reading_001").update({
      notes: "Bench calibration snapshot",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId 'id' leakage
  await assertFails(
    col.doc("reading_002").set({
      id: "reading_002",
      userId: ALICE_UID,
      sensorId: "hw_accel_1",
      sensorName: "3-Axis Accelerometer",
      sensorCategory: "MOTION",
      values: [0.0, 9.8, 0.0],
      unit: "m/s^2",
      accuracy: 3,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    col.doc("reading_001").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp
  await assertFails(
    col.doc("reading_003").set({
      userId: ALICE_UID,
      sensorId: "hw_accel_1",
      sensorName: "3-Axis Accelerometer",
      sensorCategory: "MOTION",
      values: [0.0, 9.8, 0.0],
      unit: "m/s^2",
      accuracy: 3,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(col.doc("reading_001").delete());
});

test("Authenticated owner: valid camera_capture create, query, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("camera_captures");

  await assertSucceeds(
    col.doc("cap_001").set({
      userId: ALICE_UID,
      fileName: "cap_001.jpg",
      localFilePath: "/data/user/0/com.aistudio.sensorpulse.kxmpzq/files/camera_captures/cap_001.jpg",
      lensFacing: "back",
      widthPx: 1920,
      heightPx: 1080,
      fileSizeBytes: 245678,
      flashUsed: false,
      focalLengthMm: 4.25,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(col.orderBy("createdAt", "desc").get());

  await assertSucceeds(
    col.doc("cap_001").update({
      caption: "Lab optics check",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid lensFacing
  await assertFails(
    col.doc("cap_002").set({
      userId: ALICE_UID,
      fileName: "cap_002.jpg",
      localFilePath: "/tmp/cap_002.jpg",
      lensFacing: "thermal",
      widthPx: 1920,
      heightPx: 1080,
      fileSizeBytes: 12000,
      flashUsed: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(col.doc("cap_001").delete());
});

test("Cross-user isolation: Alice cannot read, write, update, or delete Bob's telemetry or captures", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bobRoot = ctx.firestore().collection("users").doc(BOB_UID);
    await bobRoot.collection("sensor_readings").doc("bob_r1").set({
      userId: BOB_UID,
      sensorId: "hw_gyro",
      sensorName: "Gyroscope",
      sensorCategory: "MOTION",
      values: [0.01, -0.02, 0.0],
      unit: "rad/s",
      accuracy: 3,
      createdAt: created,
      updatedAt: created,
    });
    await bobRoot.collection("camera_captures").doc("bob_c1").set({
      userId: BOB_UID,
      fileName: "bob.jpg",
      localFilePath: "/data/bob/bob.jpg",
      lensFacing: "front",
      widthPx: 1280,
      heightPx: 720,
      fileSizeBytes: 98000,
      flashUsed: false,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobRootAsAlice = aliceDb.collection("users").doc(BOB_UID);

  await assertFails(bobRootAsAlice.collection("sensor_readings").get());
  await assertFails(bobRootAsAlice.collection("sensor_readings").doc("bob_r1").get());
  await assertFails(bobRootAsAlice.collection("sensor_readings").doc("bob_r1").update({ notes: "Hijack", updatedAt: new Date() }));
  await assertFails(bobRootAsAlice.collection("sensor_readings").doc("bob_r1").delete());

  await assertFails(bobRootAsAlice.collection("camera_captures").get());
  await assertFails(bobRootAsAlice.collection("camera_captures").doc("bob_c1").get());
  await assertFails(bobRootAsAlice.collection("camera_captures").doc("bob_c1").delete());
});
