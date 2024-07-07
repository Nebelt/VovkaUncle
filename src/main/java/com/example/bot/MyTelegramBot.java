package com.example.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MyTelegramBot extends TelegramLongPollingBot {

    private ConcurrentHashMap<Long, String> userPhoneNumbers = new ConcurrentHashMap<>();
    private QRParser qrParser = new QRParser();
    private ParsedQRData dto = new ParsedQRData("", "");

    @Autowired
    private Connection connection; // Внедрение бина Connection из DatabaseConfig

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId(); // Получение chatId из сообщения

            if (message.hasText() && message.getText().startsWith("/start")) {
                requestContact(chatId);
            } else if (message.hasText() && message.getText().startsWith("/count")) {
                sendMsg(chatId, "Отправьте номер телефона, количество записей которого хотите узнать. Пример: +79936891267");
            } else if (message.hasText() && message.getText().startsWith("+7")) {
                String phoneNumber = message.getText();
                int count = getCountFromDB(phoneNumber);
                sendMsg(chatId, "Количество записей для номера " + phoneNumber + ": " + count);
            } else if (message.hasContact()) {
                String phoneNumber = message.getContact().getPhoneNumber();
                String formattedPhoneNumber = formatPhoneNumber(phoneNumber);
                if (formattedPhoneNumber.equals("С Вашим номером телефона что-то не так")) {
                    sendMsg(chatId, "С Вашим номером телефона что-то не так");
                } else {
                    userPhoneNumbers.put(chatId, formattedPhoneNumber);
                    sendMsg(chatId, "Спасибо! Теперь отправьте мне QR код.");
                }


            } else if (message.hasPhoto()) {
                List<PhotoSize> photos = message.getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();
                try {
                    File file = execute(new GetFile(fileId));
                    InputStream photoStream = downloadFileAsStream(file);
                    BufferedImage bufferedImage = ImageIO.read(photoStream);
                    String qrData = qrParser.parseQRCode(bufferedImage);                //это наш QR
                    dto = qrParser.parseQRData(qrData);

                    if (qrData != null) {
                        String phoneNumber = userPhoneNumbers.get(chatId);
                        if (phoneNumber != null) {
                            String shipmentNumber = dto.getShipmentId();
                            String appName = dto.getAppName();
                            if (!appName.equals("WBDRIVE")) {
                                sendMsg(chatId, "Данные не могут быть сохранены. С Вашим QR что-то не так");
                                return;
                            }

                            saveDataToDB(chatId, phoneNumber, qrData, shipmentNumber);
                            int count = getCountFromDB(phoneNumber);
                            sendMsg(chatId, "Количество записей для номера " + phoneNumber + ": " + count);
                        } else {
                            sendMsg(chatId, "Сначала отправьте свой номер телефона.");
                            requestContact(chatId);
                        }
                    } else {
                        sendMsg(chatId, "Не удалось распознать QR код.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Произошла ошибка при обработке фото.");
                }
            }
        }
    }

    private void requestContact(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Пожалуйста, отправьте свой номер телефона, нажав кнопку ниже.");

        KeyboardButton button = new KeyboardButton();
        button.setText("Отправить номер телефона");
        button.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(button);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        keyboardMarkup.setSelective(true);

        message.setReplyMarkup(keyboardMarkup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveDataToDB(long chatId, String phoneNumber, String qrData, String shipmentNumber) {
        try {
            if (shipmentExists(shipmentNumber)) {
                sendMsg(chatId, "Запись для номера перевозки " + shipmentNumber + " уже существует.");
                return;
            }
            String query = "INSERT INTO qr_data (phone_number, qr_code, shipment_number) VALUES (?, ?, ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, phoneNumber);
            preparedStatement.setString(2, qrData);
            preparedStatement.setString(3, shipmentNumber);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            sendMsg(chatId, "Произошла ошибка при сохранении данных. Номер перевозки: " + shipmentNumber);
        }
    }

    private int getCountFromDB(String phoneNumber) {
        try {
            String query = "SELECT COUNT(*) FROM qr_data WHERE phone_number = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, phoneNumber);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0; // Если произошла ошибка или записей нет
    }

    private void sendMsg(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber.startsWith("8") || phoneNumber.startsWith("7")) {
            return "+7" + phoneNumber.substring(1);
        } else if (phoneNumber.startsWith("+7")) {
            return phoneNumber;
        } else if (phoneNumber.length() == 10) {
            return "+7" + phoneNumber;
        } else {
            return "С Вашим номером телефона что-то не так";
        }
    }

    private boolean shipmentExists(String shipmentNumber) throws SQLException {
        String query = "SELECT COUNT(*) FROM qr_data WHERE shipment_number = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(query);
        preparedStatement.setString(1, shipmentNumber);
        ResultSet resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            int count = resultSet.getInt(1);
            return count > 0;
        }
        return false;
    }

    @Override
    public String getBotUsername() {
        return "warehouseWB_bot";
    }

    @Override
    public String getBotToken() {
        return "7203671568:AAFlXTYchX4dawGGIY4s_Q03sJsyr1tgIEo";
    }
}
