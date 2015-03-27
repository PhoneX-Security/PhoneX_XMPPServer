# $Revision$
# $Date$

INSERT INTO ofVersion (name, version) VALUES ('userservice', 1);

CREATE TABLE ofPushMessages (
   msgId            BIGINT            NOT NULL AUTO_INCREMENT,
   msgAction        VARCHAR(64)       NOT NULL,
   msgTime          DATETIME          NOT NULL,
   msgExpire        DATETIME          NULL,
   forUser          VARCHAR(255)      NOT NULL,
   forResource      VARCHAR(255)      NOT NULL,
   isDurable        INT               NOT NULL,
   isUnique         INT               NOT NULL,
   aux1             TEXT              NULL,
   aux2             TEXT              NULL,
   auxData          TEXT              NULL,
   PRIMARY KEY (msgId)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPushDelivery (
   dlvrID       BIGINT                NOT NULL AUTO_INCREMENT,
   msgId        BIGINT                NOT NULL,
   dlvrTime     DATETIME              NOT NULL,
   forUser      VARCHAR(255)          NOT NULL,
   forResource  VARCHAR(255)          NOT NULL,
   dlvrStatus   INT                   NOT NULL,
   PRIMARY KEY(dlvrID),
   CONSTRAINT UNIQUE INDEX (msgId, forUser, forResource),
   FOREIGN KEY (msgId)
           REFERENCES ofPushMessages(msgId)
           ON DELETE CASCADE
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPushApple (
	JID VARCHAR(200) NOT NULL,
	devicetoken CHAR(64) NOT NULL,
  PRIMARY KEY (JID)
);
