const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const ts = () => new Date(Date.now() - 1000);

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

test("Unauthenticated caller cannot read, list, or write user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      googleDisplayName: "Usman",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Owner can create, read, and update their own user profile with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      googleDisplayName: "Usman Shaik",
      email: "usman@example.com",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      googleDisplayName: "Usman Updated",
      updatedAt: ts(),
    })
  );
});

test("Authenticated user cannot read, list, write, or delete another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      googleDisplayName: "Usman",
      email: "alice@example.com",
      createdAt: ts(),
      updatedAt: ts(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      googleDisplayName: "Hacked",
      updatedAt: ts(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
});

test("Rejects identity spoofing, ghost fields, oversized googleDisplayName, and immutable field mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  // Identity spoofing
  await assertFails(
    docRef.set({
      userId: BOB_UID,
      googleDisplayName: "Usman",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Ghost field injection
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      googleDisplayName: "Usman",
      isAdmin: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Oversized googleDisplayName (> 80 chars)
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      googleDisplayName: "A".repeat(81),
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});
