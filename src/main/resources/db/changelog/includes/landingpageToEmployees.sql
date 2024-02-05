--liquibase formatted sql
--changeset jp:landingpageToEmployees
alter table employee
    add landingpage varchar(255) null;