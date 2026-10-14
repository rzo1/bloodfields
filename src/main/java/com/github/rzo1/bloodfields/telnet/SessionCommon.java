package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.model.UnitType;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Plumbing shared by the local ({@link TelnetSession}) and proxy
 * ({@link ProxySession}) PETSCII sessions: telnet negotiation, the unit
 * key table, and clock formatting. One home, so the two sessions can
 * never drift apart again (the proxy once negotiated with a doubled
 * WILL byte the local session never sent).
 */
final class SessionCommon {

    static final char[] UNIT_KEYS = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'G'};
    static final UnitType[] UNIT_TYPES = {
            UnitType.INFANTRY, UnitType.ARCHER, UnitType.CAVALRY, UnitType.MAGE,
            UnitType.NECROMANCER, UnitType.HEALER, UnitType.CATAPULT, UnitType.ASSASSIN,
            UnitType.GOLEM, UnitType.PIKEMAN, UnitType.GENERAL
    };

    private SessionCommon() {}

    /**
     * Opening negotiation: WILL/DO BINARY (PETSCII lives above 0x7F),
     * WILL ECHO + WILL/DO SGA for character-at-a-time input, then a
     * cleared uppercase white screen. {@link IacFilter} knows these
     * offers and treats the client's replies as acknowledgements.
     */
    static void negotiate(OutputStream out) throws IOException {
        writeIac(out, IacFilter.WILL, IacFilter.OPT_BINARY);
        writeIac(out, IacFilter.DO, IacFilter.OPT_BINARY);
        writeIac(out, IacFilter.WILL, IacFilter.OPT_ECHO);
        writeIac(out, IacFilter.WILL, IacFilter.OPT_SGA);
        writeIac(out, IacFilter.DO, IacFilter.OPT_SGA);
        out.write(Petscii.CLEAR);
        out.write(Petscii.UPPERCASE_MODE);
        out.write(Petscii.WHITE);
        out.flush();
    }

    private static void writeIac(OutputStream out, int cmd, int option) throws IOException {
        out.write(IacFilter.IAC);
        out.write(cmd);
        out.write(option);
    }

    static UnitType typeForKey(int k) {
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            if (UNIT_KEYS[i] == k) return UNIT_TYPES[i];
        }
        return null;
    }

    static String mmss(double simSeconds) {
        long t = (long) simSeconds;
        long m = t / 60;
        long s = t % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }
}
