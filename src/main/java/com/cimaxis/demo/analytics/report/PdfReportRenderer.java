package com.cimaxis.demo.analytics.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Construccion de los PDF de CIMAxis con OpenPDF: A4 horizontal, banda de marca
 * en cada pagina (logo o "CIMA", titulo y subtitulo), pie "Generado por CIMAxis ·
 * Pagina X de Y" y piezas reutilizables (secciones, tarjetas, tablas, mensaje de
 * vacio). No guarda estado: se crea un documento por llamada.
 */
public final class PdfReportRenderer {

    public static final String SIN_DATOS = "No hay datos para el período seleccionado";

    static final float MARGEN_LATERAL = 48f;
    static final float ALTO_BANDA = 70f;
    static final float MARGEN_SUPERIOR = ALTO_BANDA + 30f;
    static final float MARGEN_INFERIOR = 52f;

    /** Contenido del documento; recibe el documento ya abierto. */
    @FunctionalInterface
    public interface Content {
        void write(Document document) throws DocumentException;
    }

    private PdfReportRenderer() {
    }

    public static byte[] render(String titulo, String subtitulo, byte[] logo, Content contenido) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), MARGEN_LATERAL, MARGEN_LATERAL,
                    MARGEN_SUPERIOR, MARGEN_INFERIOR);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Marco(titulo, subtitulo, imagen(logo)));
            document.addTitle(titulo);
            document.addCreator("CIMAxis");
            document.open();
            contenido.write(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando el archivo PDF: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ piezas

    public static Font fuente(float tamano, int estilo, Color color) {
        return new Font(Font.HELVETICA, tamano, estilo, color);
    }

    public static Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, fuente(13, Font.BOLD, ReportBranding.ROJO));
        p.setSpacingBefore(14);
        p.setSpacingAfter(8);
        return p;
    }

    public static Paragraph nota(String texto) {
        Paragraph p = new Paragraph(texto, fuente(9, Font.NORMAL, ReportBranding.GRIS_TEXTO));
        p.setSpacingAfter(6);
        return p;
    }

    public static Paragraph sinDatos() {
        Paragraph p = new Paragraph(SIN_DATOS, fuente(14, Font.NORMAL, ReportBranding.GRIS_TEXTO));
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(90);
        return p;
    }

    /** Tarjeta de indicador: nombre, valor grande y una linea inferior (variacion) con su color. */
    public static PdfPCell tarjeta(String nombre, String valor, String pie, Color colorPie, String comparacion) {
        PdfPCell celda = new PdfPCell();
        celda.setBorderColor(ReportBranding.GRIS_BORDE);
        celda.setBorderWidth(0.75f);
        celda.setBorderWidthLeft(3f);
        celda.setBorderColorLeft(ReportBranding.ROJO);
        celda.setPadding(10);
        celda.setPaddingBottom(12);
        celda.setUseVariableBorders(true);

        Paragraph etiqueta = new Paragraph(nombre, fuente(9, Font.BOLD, ReportBranding.GRIS_TEXTO));
        Paragraph numero = new Paragraph(valor, fuente(19, Font.BOLD, ReportBranding.TINTA));
        numero.setSpacingBefore(4);
        Phrase linea = new Phrase();
        if (pie.startsWith("▲") || pie.startsWith("▼")) {
            // Helvetica (WinAnsi) no tiene ▲ ▼: la flecha va en ZapfDingbats, que si la incluye.
            linea.add(new Chunk(pie.substring(0, 1), new Font(Font.ZAPFDINGBATS, 7, Font.NORMAL, colorPie)));
            linea.add(new Chunk(pie.substring(1), fuente(9, Font.BOLD, colorPie)));
        } else {
            linea.add(new Chunk(pie, fuente(9, Font.BOLD, colorPie)));
        }
        if (comparacion != null) {
            linea.add(new Chunk("  " + comparacion, fuente(8, Font.NORMAL, ReportBranding.GRIS_TEXTO)));
        }
        Paragraph variacion = new Paragraph(linea);
        variacion.setSpacingBefore(4);
        celda.addElement(etiqueta);
        celda.addElement(numero);
        celda.addElement(variacion);
        return celda;
    }

    /** Tabla con encabezado de marca que se repite en cada pagina, filas alternas y numeros a la derecha. */
    public static PdfPTable tabla(ReportTable tabla) throws DocumentException {
        List<ReportColumn> columnas = tabla.columns();
        PdfPTable t = new PdfPTable(columnas.size());
        t.setWidthPercentage(100);
        float[] anchos = new float[columnas.size()];
        for (int i = 0; i < anchos.length; i++) anchos[i] = columnas.get(i).width();
        t.setWidths(anchos);
        t.setHeaderRows(1);
        t.setSpacingBefore(4);

        for (ReportColumn c : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(c.title(), fuente(8.5f, Font.BOLD, ReportBranding.BLANCO)));
            celda.setBackgroundColor(ReportBranding.ROJO);
            celda.setBorderColor(ReportBranding.ROJO);
            celda.setPadding(6);
            celda.setHorizontalAlignment(c.kind().numeric() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(celda);
        }
        int fila = 0;
        for (List<Object> datos : tabla.rows()) {
            Color fondo = fila++ % 2 == 1 ? ReportBranding.GRIS_FONDO : ReportBranding.BLANCO;
            for (int i = 0; i < columnas.size(); i++) {
                ReportColumn c = columnas.get(i);
                Object valor = i < datos.size() ? datos.get(i) : null;
                PdfPCell celda = new PdfPCell(new Phrase(ReportTable.formatear(c.kind(), valor),
                        fuente(8.5f, Font.NORMAL, ReportBranding.TINTA)));
                celda.setBackgroundColor(fondo);
                celda.setBorder(Rectangle.BOTTOM);
                celda.setBorderColor(ReportBranding.GRIS_BORDE);
                celda.setPadding(5);
                celda.setHorizontalAlignment(c.kind().numeric() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
                t.addCell(celda);
            }
        }
        return t;
    }

    /** Lista de definiciones: nombre en negrilla y explicacion. */
    public static PdfPTable definiciones(List<String[]> pares) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[] {1.2f, 4f});
        for (String[] par : pares) {
            PdfPCell nombre = new PdfPCell(new Phrase(par[0], fuente(9, Font.BOLD, ReportBranding.TINTA)));
            PdfPCell texto = new PdfPCell(new Phrase(par[1], fuente(9, Font.NORMAL, ReportBranding.TINTA)));
            for (PdfPCell c : new PdfPCell[] {nombre, texto}) {
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColor(ReportBranding.GRIS_BORDE);
                c.setPadding(5);
            }
            t.addCell(nombre);
            t.addCell(texto);
        }
        return t;
    }

    /** Titulo de seccion y su contenido en un bloque que no se separa entre paginas. */
    public static PdfPTable bloque(Paragraph titulo, PdfPTable contenido) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        PdfPCell celdaTitulo = new PdfPCell();
        celdaTitulo.setBorder(Rectangle.NO_BORDER);
        celdaTitulo.setPadding(0);
        celdaTitulo.addElement(titulo);
        t.addCell(celdaTitulo);
        PdfPCell celdaContenido = new PdfPCell(contenido);
        celdaContenido.setBorder(Rectangle.NO_BORDER);
        celdaContenido.setPadding(0);
        t.addCell(celdaContenido);
        return t;
    }

    /** Caja destacada con un valor total y una nota. */
    public static PdfPTable destacado(String etiqueta, String valor, String nota) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(40);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingAfter(10);
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(ReportBranding.GRIS_FONDO);
        celda.setBorder(Rectangle.LEFT);
        celda.setBorderWidthLeft(3f);
        celda.setBorderColorLeft(ReportBranding.ROJO);
        celda.setUseVariableBorders(true);
        celda.setPadding(10);
        celda.addElement(new Paragraph(etiqueta, fuente(9, Font.BOLD, ReportBranding.GRIS_TEXTO)));
        Paragraph v = new Paragraph(valor, fuente(20, Font.BOLD, ReportBranding.TINTA));
        v.setSpacingBefore(2);
        celda.addElement(v);
        if (nota != null) celda.addElement(new Paragraph(nota, fuente(9, Font.NORMAL, ReportBranding.GRIS_TEXTO)));
        t.addCell(celda);
        return t;
    }

    private static Image imagen(byte[] logo) {
        if (logo == null || logo.length == 0) return null;
        try {
            return Image.getInstance(logo);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ marco de pagina

    /** Banda de marca arriba y pie con "Pagina X de Y" en cada pagina. */
    static final class Marco extends PdfPageEventHelper {

        private final String titulo;
        private final String subtitulo;
        private final Image logo;
        private BaseFont normal;
        private PdfTemplate total;

        Marco(String titulo, String subtitulo, Image logo) {
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.logo = logo;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            total = writer.getDirectContent().createTemplate(40, 14);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle pagina = document.getPageSize();
            float ancho = pagina.getWidth();
            float alto = pagina.getHeight();

            cb.saveState();
            cb.setColorFill(ReportBranding.ROJO);
            cb.rectangle(0, alto - ALTO_BANDA, ancho, ALTO_BANDA);
            cb.fill();
            cb.setColorFill(ReportBranding.ROJO_OSCURO);
            cb.rectangle(0, alto - ALTO_BANDA - 4, ancho, 4);
            cb.fill();
            cb.restoreState();

            float xTexto = MARGEN_LATERAL;
            boolean conLogo = false;
            if (logo != null) {
                try {
                    Image img = Image.getInstance(logo);
                    img.scaleToFit(130, 38);
                    img.setAbsolutePosition(MARGEN_LATERAL, alto - ALTO_BANDA + (ALTO_BANDA - img.getScaledHeight()) / 2);
                    cb.addImage(img);
                    xTexto = MARGEN_LATERAL + img.getScaledWidth() + 22;
                    conLogo = true;
                } catch (Exception e) {
                    conLogo = false;
                }
            }
            if (!conLogo) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase("CIMA", fuente(24, Font.BOLD, ReportBranding.BLANCO)),
                        MARGEN_LATERAL, alto - ALTO_BANDA / 2 - 8, 0);
                xTexto = MARGEN_LATERAL + 92;
                cb.saveState();
                cb.setColorStroke(new Color(255, 255, 255, 120));
                cb.setLineWidth(0.8f);
                cb.moveTo(xTexto - 16, alto - ALTO_BANDA + 16);
                cb.lineTo(xTexto - 16, alto - 16);
                cb.stroke();
                cb.restoreState();
            }
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(titulo, fuente(16, Font.BOLD, ReportBranding.BLANCO)),
                    xTexto, alto - 32, 0);
            if (subtitulo != null && !subtitulo.isBlank()) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(subtitulo, fuente(9, Font.NORMAL, new Color(255, 236, 236))),
                        xTexto, alto - 50, 0);
            }

            // Pie: linea fina y "Generado por CIMAxis · Pagina X de Y" centrado.
            cb.saveState();
            cb.setColorStroke(ReportBranding.GRIS_BORDE);
            cb.setLineWidth(0.5f);
            cb.moveTo(MARGEN_LATERAL, MARGEN_INFERIOR - 18);
            cb.lineTo(ancho - MARGEN_LATERAL, MARGEN_INFERIOR - 18);
            cb.stroke();
            String texto = "Generado por CIMAxis · Página " + writer.getPageNumber() + " de ";
            float tamano = 8f;
            float anchoTexto = normal.getWidthPoint(texto, tamano);
            float anchoTotal = normal.getWidthPoint("00", tamano);
            float x = (ancho - anchoTexto - anchoTotal) / 2;
            float y = MARGEN_INFERIOR - 32;
            cb.beginText();
            cb.setFontAndSize(normal, tamano);
            cb.setColorFill(ReportBranding.GRIS_TEXTO);
            cb.setTextMatrix(x, y);
            cb.showText(texto);
            cb.endText();
            cb.addTemplate(total, x + anchoTexto, y);
            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(normal, 8f);
            total.setColorFill(ReportBranding.GRIS_TEXTO);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
