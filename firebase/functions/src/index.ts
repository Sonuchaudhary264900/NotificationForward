import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// Avoids visually ambiguous characters (0/O, 1/I) since a human reads this off one screen and
// types it into another.
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 6;
const CODE_TTL_MS = 10 * 60 * 1000;

function generateCode(): string {
  let out = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    out += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  }
  return out;
}

/**
 * Child device: creates (or reuses) its family and issues a short-lived pairing code. The
 * calling device is not made a "paired" member of anything new here — it already owns the
 * family it just created. Reuses an existing family for this device so calling it again after a
 * code expires doesn't fragment the account.
 */
export const createPairingCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  const childDeviceId = request.data?.childDeviceId;
  const displayName = typeof request.data?.displayName === "string" ? request.data.displayName : "Child device";
  if (typeof childDeviceId !== "string" || !childDeviceId) {
    throw new HttpsError("invalid-argument", "childDeviceId is required.");
  }

  const existingFamily = await db
    .collection("families")
    .where("ownerUid", "==", uid)
    .limit(1)
    .get();

  let familyId: string;
  if (!existingFamily.empty) {
    familyId = existingFamily.docs[0].id;
  } else {
    const familyRef = db.collection("families").doc();
    familyId = familyRef.id;
    await familyRef.set({
      ownerUid: uid,
      members: [uid],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  }

  await db
    .collection("families").doc(familyId)
    .collection("devices").doc(childDeviceId)
    .set(
      {
        role: "CHILD",
        displayName,
        platform: "android",
        pairedAt: admin.firestore.FieldValue.serverTimestamp(),
        lastSeenAt: admin.firestore.FieldValue.serverTimestamp(),
        status: "PENDING",
      },
      { merge: true }
    );

  let code = "";
  for (let attempt = 0; attempt < 5; attempt++) {
    const candidate = generateCode();
    const codeSnap = await db.collection("pairingCodes").doc(candidate).get();
    if (!codeSnap.exists) {
      code = candidate;
      break;
    }
  }
  if (!code) {
    throw new HttpsError("resource-exhausted", "Could not generate a unique code — try again.");
  }

  const expiresAtMs = Date.now() + CODE_TTL_MS;
  await db.collection("pairingCodes").doc(code).set({
    familyId,
    childDeviceId,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMs),
    consumed: false,
    consumedByDeviceId: null,
  });

  return { code, familyId, expiresAt: expiresAtMs };
});

/**
 * Parent device: redeems a pairing code. Runs as one atomic transaction because the parent's
 * uid is not yet a member of the family when this call starts, so it can't be granted rule-based
 * write access to join without either a race-prone multi-step client write sequence or rules
 * permissive enough to let any client join any family by guessing a code before it's consumed.
 */
export const consumePairingCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  const code = String(request.data?.code ?? "").trim().toUpperCase();
  const parentDeviceId = request.data?.parentDeviceId;
  const displayName = typeof request.data?.displayName === "string" ? request.data.displayName : "Parent device";
  if (!code || typeof parentDeviceId !== "string" || !parentDeviceId) {
    throw new HttpsError("invalid-argument", "code and parentDeviceId are required.");
  }

  const codeRef = db.collection("pairingCodes").doc(code);

  const familyId = await db.runTransaction(async (tx) => {
    const codeSnap = await tx.get(codeRef);
    if (!codeSnap.exists) {
      throw new HttpsError("not-found", "Invalid pairing code.");
    }
    const codeData = codeSnap.data()!;
    if (codeData.consumed) {
      throw new HttpsError("failed-precondition", "This code has already been used.");
    }
    const expiresAt = codeData.expiresAt as admin.firestore.Timestamp;
    if (expiresAt.toMillis() < Date.now()) {
      throw new HttpsError("deadline-exceeded", "This code has expired.");
    }

    const familyIdFromCode = codeData.familyId as string;
    const childDeviceId = codeData.childDeviceId as string;
    if (childDeviceId === parentDeviceId) {
      throw new HttpsError("invalid-argument", "A device can't pair with itself.");
    }

    const familyRef = db.collection("families").doc(familyIdFromCode);
    const familySnap = await tx.get(familyRef);
    if (!familySnap.exists) {
      throw new HttpsError("not-found", "Family no longer exists.");
    }

    const parentDeviceRef = familyRef.collection("devices").doc(parentDeviceId);
    const childDeviceRef = familyRef.collection("devices").doc(childDeviceId);
    const now = admin.firestore.FieldValue.serverTimestamp();

    tx.update(familyRef, { members: admin.firestore.FieldValue.arrayUnion(uid) });
    tx.set(parentDeviceRef, {
      role: "PARENT",
      displayName,
      platform: "android",
      fcmToken: null,
      pairedAt: now,
      lastSeenAt: now,
      consentAcknowledgedAt: null,
      status: "ACTIVE",
    });
    tx.update(childDeviceRef, { status: "ACTIVE", lastSeenAt: now });
    tx.update(codeRef, { consumed: true, consumedByDeviceId: parentDeviceId });

    return familyIdFromCode;
  });

  return { familyId };
});
