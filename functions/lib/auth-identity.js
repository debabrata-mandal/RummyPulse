"use strict";

function authenticationProvider(token, providerData) {
  const firebaseProvider = token && token.firebase &&
    typeof token.firebase.sign_in_provider === "string" ?
    token.firebase.sign_in_provider.trim() : "";
  const linkedProvider = Array.isArray(providerData) ? providerData.find((entry) =>
    entry && typeof entry.providerId === "string" && entry.providerId.trim()) : null;
  const providerId = firebaseProvider || linkedProvider?.providerId || "";
  switch (providerId) {
    case "google.com":
      return "Google";
    case "password":
      return "Password";
    case "phone":
      return "Phone";
    case "anonymous":
      return "Anonymous";
    default:
      return "Firebase";
  }
}

module.exports = {authenticationProvider};
