/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2017 Maxime Dor
 *
 * https://max.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.mxisd.mapping;

import io.kamax.mxisd.exception.BadRequestException;
import io.kamax.mxisd.lookup.ThreePidValidation;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class MappingManager {

    private Logger log = LoggerFactory.getLogger(MappingManager.class);

    private Map<String, Session> sessions = new HashMap<>();
    private Timer cleaner;

    MappingManager() {
        cleaner = new Timer();
        cleaner.schedule(new TimerTask() {
            @Override
            public void run() {
                List<Session> sList = new ArrayList<>(sessions.values());
                for (Session s : sList) {
                    if (s.timestamp.plus(24, ChronoUnit.HOURS).isBefore(Instant.now())) { // TODO config timeout
                        log.info("Session {} is obsolete, removing", s.sid);

                        sessions.remove(s.sid);
                    }
                }
            }
        }, 0, 10 * 1000);  // TODO config delay
    }

    public String create(MappingSession data) {
        String sid;
        do {
            sid = Long.toString(System.currentTimeMillis());
        } while (sessions.containsKey(sid));

        String threePidHash = data.getMedium() + data.getValue();
        // TODO think how to handle different requests for the same e-mail
        Session session = new Session(sid, threePidHash, data);
        sessions.put(sid, session);

        log.info("Created new session {} to validate {} {}", sid, session.medium, session.address);
        return sid;
    }

    public void validate(String sid, String secret, String token) {
        Session s = sessions.get(sid);
        if (s == null || !StringUtils.equals(s.secret, secret)) {
            throw new BadRequestException("sid or secret are not valid");
        }

        // TODO actually check token

        s.isValidated = true;
        s.validationTimestamp = Instant.now();
    }

    public Optional<ThreePidValidation> getValidated(String sid, String secret) {
        Session s = sessions.get(sid);
        if (s != null && StringUtils.equals(s.secret, secret)) {
            return Optional.of(new ThreePidValidation(s.medium, s.address, s.validationTimestamp));
        }

        return Optional.empty();
    }

    public void bind(String sid, String secret, String mxid) {
        Session s = sessions.get(sid);
        if (s == null || !StringUtils.equals(s.secret, secret)) {
            throw new BadRequestException("sid or secret are not valid");
        }

        log.info("Performed bind for mxid {}", mxid);
        // TODO perform bind, whatever it is
    }

    private class Session {

        private String sid;
        private String hash;
        private Instant timestamp;
        private Instant validationTimestamp;
        private boolean isValidated;
        private String secret;
        private String medium;
        private String address;

        public Session(String sid, String hash, MappingSession data) {
            this.sid = sid;
            this.hash = hash;
            timestamp = Instant.now();
            validationTimestamp = Instant.now();
            secret = data.getSecret();
            medium = data.getMedium();
            address = data.getValue();
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public Instant getValidationTimestamp() {
            return validationTimestamp;
        }

        public void setValidationTimestamp(Instant validationTimestamp) {
            this.validationTimestamp = validationTimestamp;
        }

        public boolean isValidated() {
            return isValidated;
        }

        public void setValidated(boolean validated) {
            isValidated = validated;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getMedium() {
            return medium;
        }

        public void setMedium(String medium) {
            this.medium = medium;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

}
