#![forbid(unsafe_code)]

//! Minimal shared-core boundary established by the Phase 0 repository reset.
//!
//! Messaging, cryptography, storage, and transport behavior intentionally arrive in
//! later security-gated phases. This crate must not be treated as a secure messenger.

/// Public product identity exposed to native shells during the reset phase.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BuildIdentity {
    /// Human-readable product name.
    pub product_name: &'static str,
    /// Current architecture milestone.
    pub architecture_phase: &'static str,
}

/// Returns the non-secret build identity shared by platform shells.
#[must_use]
pub const fn build_identity() -> BuildIdentity {
    BuildIdentity {
        product_name: "TrustKin",
        architecture_phase: "phase0-reset",
    }
}

#[cfg(test)]
mod tests {
    use super::{BuildIdentity, build_identity};

    #[test]
    fn reports_the_trustkin_reset_identity() {
        assert_eq!(
            build_identity(),
            BuildIdentity {
                product_name: "TrustKin",
                architecture_phase: "phase0-reset",
            }
        );
    }
}
