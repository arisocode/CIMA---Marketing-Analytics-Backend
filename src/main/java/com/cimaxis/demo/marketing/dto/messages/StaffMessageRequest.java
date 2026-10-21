package com.cimaxis.demo.marketing.dto.messages;

/** alsoEmail=true envia tambien el mensaje por correo al cliente (respeta el cupo diario). */
public record StaffMessageRequest(String body, Boolean alsoEmail) {
}
