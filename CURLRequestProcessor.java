import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;

/**
 * Данный класс предназначен для генерации curl HTTP/HTTPs запросов, их отправки, обработки
 * и получения ответа
 * @author rmussalimov
 */
public final class CURLRequestProcessor
{
    private CURLRequestProcessor()
    {

    }

    private static final String CURL_HEADER_IDENTIFIER = "-H"; // заголовок
    private static final String CURL_SIMPLE_BODY_IDENTIFIER = "-d"; // простое body запроса
    private static final String CURL_MULTIPART_BODY_IDENTIFIER = "-F"; // multipart body запроса

    // При TLS соединении данную строчку нужно убрать из ответа
    private static final String GOST_ENGINE_ALREADY_LOADED = "GOST engine already loaded";


    /**
     * Строит финальный curl запрос, отправляет его и получает ответ
     * @param httpMethod - HTTP метод (POST/PUT/GET и т.д.)
     * @param URL - URL к которому необходимо совершить запрос
     * @param headers - Сгенерированные HTTP заголовки запроса
     * @param body - Сгенерированное тело HTTP запроса
     * @param includeResponseHeaders - Необходимость включения заголовков ответа в результат
     * @return Результат выполнения команды в String
     * @throws IOException
     */
    public static String buildCurlProcessBuilderExecuteAndGetResponse(String httpMethod, String URL, List<String> headers, List<String> body, boolean includeResponseHeaders) throws IOException
    {
        List<String> finalCommands = new ArrayList<>();

        finalCommands.add("curl");
        finalCommands.add("-s");
        finalCommands.add("-k");

        if (includeResponseHeaders)
        {
            finalCommands.add("-i");
        }

        finalCommands.add("-X");
        finalCommands.add(httpMethod);

        finalCommands.add(URL);

        if (headers != null)
        {
            finalCommands.addAll(headers);
        }

        if (body != null)
        {
            finalCommands.addAll(body);
        }

        ProcessBuilder pb = new ProcessBuilder(finalCommands);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        return getResponseAsString(p.getInputStream());
    }

    /**
     * Метод генерирует HTTP заголовки для curl запроса
     * @param mapOfHeadersToAdd Map, в которой содержатся headers (ключ-значение)
     * @return Сгенерированные headers
     */
    public static List<String> generateHeadersForCurlRequest(Map<String, String> mapOfHeadersToAdd)
    {
        Iterator<Entry<String, String>> headersIt = mapOfHeadersToAdd.entrySet().iterator();
        List<String> headerCommand = new ArrayList<>();

        while (headersIt.hasNext())
        {
            Map.Entry pair = (Map.Entry) headersIt.next();
            headerCommand.add(CURL_HEADER_IDENTIFIER);
            headerCommand.add(pair.getKey() + ": " + pair.getValue());
        }

        return headerCommand;
    }

    /**
     * Метод генерирует multipart (multipart/form-data) тело для curl запроса
     * @param mapOfEntitiesToAdd Map, в которой содержатся Entity (ключ-значение) для добавления в body
     * @return Сгенерированный multipart body
     */
    public static List<String> generateMultipartBodyForCurlRequest(Map<String, String> mapOfEntitiesToAdd)
    {
        Iterator<Entry<String, String>> multipartBodyIt = mapOfEntitiesToAdd.entrySet().iterator();
        List<String> bodyCommand = new ArrayList<>();

        while (multipartBodyIt.hasNext())
        {
            Map.Entry pair = (Map.Entry) multipartBodyIt.next();
            bodyCommand.add(CURL_MULTIPART_BODY_IDENTIFIER);
            bodyCommand.add(pair.getKey() + "=" + pair.getValue());
        }

        return bodyCommand;
    }

    /**
     * Метод генерирует Simple Body запроса
     * @param simpleBodyValue Значение simpleBody
     * @return
     */
    public static List<String> generateSimpleBodyForCurlRequest(String simpleBodyValue)
    {
        return new ArrayList<>(Arrays.asList(CURL_SIMPLE_BODY_IDENTIFIER, simpleBodyValue));
    }

    /**
     * Метод получает значение желаемого заголовка ответа
     * @param curlResponse Ответ от сервиса
     * @param headerNameToGet Имя заголовка, который нужно получить
     * @return значение желаемого заголовка
     */
    public static String getHeaderValue(String curlResponse, String headerNameToGet)
    {
        String [] headersNamesValues = curlResponse.split(System.getProperty("line.separator"));

        for (int i = 0; i < headersNamesValues.length; i++)
        {
            String [] headerNameAndValue = headersNamesValues[i].split(": ");

            if (headerNameAndValue.length == 2)
            {
                if (headerNameAndValue[0].equals(headerNameToGet))
                {
                    return headerNameAndValue[1];
                }
            }
        }

        return "";
    }

     /**
     * Конвертирует ответ (InputStream) в строку
     * @param isToConvertToString InputStream из ответа для конвертации в String
     * @return Конвертированный ответ в String
     * @throws IOException
     */
    private static String getResponseAsString(InputStream isToConvertToString) throws IOException
    {
        StringWriter writer = new StringWriter();
        IOUtils.copy(isToConvertToString, writer, "UTF-8");
        return removeGOSTEngineString(writer.toString());
    }

    /**
     * Удаляет строку "GOST engine already loaded" из ответа
     * @param curlResponseValueWithProbGOSTString - ответ строкой
     * @return обработанный ответ
     */
    private static String removeGOSTEngineString(String curlResponseValueWithProbGOSTString)
    {
        return curlResponseValueWithProbGOSTString.contains(GOST_ENGINE_ALREADY_LOADED) ?
                curlResponseValueWithProbGOSTString.substring(curlResponseValueWithProbGOSTString.indexOf(System.getProperty("line.separator")) + 1) :
                curlResponseValueWithProbGOSTString;
    }
}