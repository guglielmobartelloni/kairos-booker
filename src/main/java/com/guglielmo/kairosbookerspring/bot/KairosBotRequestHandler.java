package com.guglielmo.kairosbookerspring.bot;

import com.github.kshashov.telegram.api.TelegramMvcController;
import com.github.kshashov.telegram.api.bind.annotation.BotController;
import com.github.kshashov.telegram.api.bind.annotation.BotPathVariable;
import com.github.kshashov.telegram.api.bind.annotation.request.MessageRequest;
import com.guglielmo.kairosbookerspring.Booker;
import com.guglielmo.kairosbookerspring.Lesson;
import com.guglielmo.kairosbookerspring.db.chat.ChatHistory;
import com.guglielmo.kairosbookerspring.db.chat.ChatHistoryRepository;
import com.guglielmo.kairosbookerspring.db.devUser.DevUser;
import com.guglielmo.kairosbookerspring.db.devUser.DevUserRepository;
import com.guglielmo.kairosbookerspring.db.lessonToBook.LessonToBook;
import com.guglielmo.kairosbookerspring.db.lessonToBook.LessonToBookRepository;
import com.guglielmo.kairosbookerspring.db.user.KairosUser;
import com.guglielmo.kairosbookerspring.db.user.UserRepository;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@BotController
@Slf4j
@EnableScheduling
/**
 * This class rapresent the bot that handles user's command sent through telegram
 */
public class KairosBotRequestHandler implements TelegramMvcController {

    private final UserRepository userRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final LessonToBookRepository lessonToBookRepository;
    private final DevUserRepository devUserRepository;
    private final KairosBotMessanger messenger;
    private final TelegramBot devBot;


    private Booker booker;

    @Value("${bot.token}")
    private String botToken;

    /**
     * Constructor with autowired fields
     *
     * @param userRepository Repository to store user credentials
     */
    @Autowired
    public KairosBotRequestHandler(UserRepository userRepository, ChatHistoryRepository chatHistoryRepository, LessonToBookRepository lessonToBookRepository, DevUserRepository devUserRepository, KairosBotMessanger kairosBotMessanger, @Value("${bot.dev.token}") String devToken) {
        this.userRepository = userRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.lessonToBookRepository = lessonToBookRepository;
        this.devUserRepository = devUserRepository;
        this.booker = new Booker();
        this.devBot = new TelegramBot(devToken);
        this.messenger = kairosBotMessanger;
    }

    @Override
    public String getToken() {
        return botToken;
    }

    /**
     * Method to welcome for new user
     *
     * @param chat The rapresentation of the chat with the user
     */
    @MessageRequest("/start")
    public String welcomeUser(Chat chat) {
        logMessage("/start", chat.id());
        log.info(chat.id().toString());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isPresent())
            return "Bentornato su KairosBot!";
        final KairosUser kairosUser = userRepository.findByChadId(chat.id())
                .orElse(KairosUser.builder()
                        .chadId(chat.id())
                        .username(chat.username())
                        .addingMatricola(false)
                        .addingPassword(false)
                        .addingAutoBooking(false)
                        .removingAutoBooking(false)
                        .writingReport(false)
                        .build());
        userRepository.save(kairosUser);
        return "Benvenuto su KairosBot, il bot telegram per prenotare il tuo posto in ateneo!\n\n" +
                "Per inizizare utilizza i comandi:\n" +
                "- /matricola per inserire la tua matricola;\n" +
                "- /password per inserire la tua password;\n" +
                "Dopo aver eseguito il login usa /prenota per prenotare il tuo posto a lezione.\n\n" +
                "Altri comandi utili:\n" +
                "- /dati per visualizzare le tue informazioni;\n" +
                "- /auto_prenota per avviare la procedura di prenotazione automatica;\n" +
                "- /rimuovi_corsi per rimuovere i corsi in auto prenotazione\n" +
                "- /stop per arrestare la procedura di prenotazione automatica;\n" +
                "- /logout per eliminare tutti i tuoi dati.";
    }

    /**
     * Method to request the username of a user
     *
     * @param chat The rapresentation of the chat with the user
     */
    @MessageRequest("/matricola")
    public String setMatricola(Chat chat) {
        logMessage("/matricola", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (!optionalUser.isPresent())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return "Non puoi usare questo comando adesso!";
        kairosUser.setAddingMatricola(true);
        userRepository.save(kairosUser);
        return "Inserisci adesso la tua matricola";
    }

    /**
     * Method to request the password of a user
     *
     * @param chat The rapresentation of the chat with the user
     */
    @MessageRequest("/password")
    public String setPassword(Chat chat) {
        logMessage("/password", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isEmpty())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return "Non puoi usare questo comando adesso!";
        kairosUser.setAddingPassword(true);
        userRepository.save(kairosUser);
        return "Inserisci adesso la tua password";
    }

    /**
     * Method to display a menu with the lessons to book
     *
     * @param chat The rapresentation of the chat with the user
     * @return The lessons menu
     */
    @MessageRequest("/prenota")
    public BaseRequest getCurses(Chat chat) {
        logMessage("/prenota", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isEmpty())
            return new SendMessage(chat.id(), "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start");
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return new SendMessage(chat.id(), "Non puoi usare un comando adesso!");
        if (kairosUser.getPassword() == null || kairosUser.getMatricola() == null)
            return new SendMessage(chat.id(), "Non è stato effettuato il login.\n" +
                    "Inserire /matricola e /password");
        messenger.sendMessageTo(chat.id(), "Ricerca delle lezioni in corso...");
        try {
            final List<Lesson> courses = booker.getCourses(kairosUser.getMatricola(), kairosUser.getPassword());
            final ReplyKeyboardMarkup lessonsMenu = new ReplyKeyboardMarkup(new KeyboardButton("Lista Corsi"));
            courses.forEach(e -> lessonsMenu.addRow(e.getCourseName() + " - " + e.getDate() + " " + (e.isBooked() ? "[🟢]" : "[🔴]")));
            //updateLessons(courses, user);
            final SendMessage request = new SendMessage(kairosUser.getChadId(), "Scegli un corso")
                    .parseMode(ParseMode.HTML)
                    .disableWebPagePreview(true)
                    .disableNotification(true)
                    .replyMarkup(lessonsMenu);
            return request;
        } catch (Exception e) {
            return loginError(chat.id());
        }
    }

    /**
     * Method to start auto booking procedure
     *
     * @param chat The representation of the chat with the user
     * @return The lessons menu
     */
    @MessageRequest("/start")
    public String startAutoBooking(Chat chat) {
        final Optional<KairosUser> optionalKairosUser = userRepository.findByChadId(chat.id());
        if (optionalKairosUser.isEmpty())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalKairosUser.get();
        if (checkCommandRunning(kairosUser))
            return "Non puoi usare un comando adesso!";
        if (kairosUser.isAutoBooking())
            return "Procedura di auto prenotazione già attiva!";
        kairosUser.setAutoBooking(true);
        userRepository.save(kairosUser);
        return "Procedura di auto prenotazione attivata!";
    }

    /**
     * Method to display a menu with the lessons to book
     * and set the properties to start automatic booking
     *
     * @param chat The representation of the chat with the user
     * @return The lessons menu
     */
    @MessageRequest("/scegli_corsi")
    public BaseRequest chooseAutoBooking(Chat chat) {
        logMessage("/scegli_corsi", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (!optionalUser.isPresent())
            return new SendMessage(chat.id(), "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start");
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return new SendMessage(chat.id(), "Non puoi usare un comando adesso!");
        if (kairosUser.getPassword() == null || kairosUser.getMatricola() == null)
            return new SendMessage(chat.id(), "Non è stato effettuato il login.\n" +
                    "Inserire /matricola e /password");
        try {
            kairosUser.setAddingAutoBooking(true);
            userRepository.save(kairosUser);
            messenger.sendMessageTo(chat.id(), "Ricerca delle lezioni in corso...");
            final List<String> courses = booker.getCoursesName(kairosUser.getMatricola(), kairosUser.getPassword());
            final ReplyKeyboardMarkup lessonsMenu = new ReplyKeyboardMarkup(new KeyboardButton("Lista Corsi"));
            courses.forEach(e -> lessonsMenu.addRow(e));
            lessonsMenu.addRow("FINE");
            final SendMessage request = new SendMessage(kairosUser.getChadId(),
                    "Seleziona i corsi da prenotare automaticamente.\n"
                            + "Quando hai finito digita 'FINE' per arrestare il processo di selezione e confermare le tue scelte.")
                    .parseMode(ParseMode.HTML)
                    .disableWebPagePreview(true)
                    .disableNotification(true)
                    .replyMarkup(lessonsMenu);
            return request;
        } catch (Exception e) {
            return loginError(chat.id());
        }
    }

    /**
     * Method to display a menu with the lessons to book
     *
     * @param chat The rapresentation of the chat with the user
     * @return The data of the user
     */
    @MessageRequest("/dati")
    public String getUserData(Chat chat) {
        logMessage("/dati", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isEmpty())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return "Non puoi usare un comando adesso!";
        String userData = "I tuoi dati: \n" +
                "- Matricola: " + kairosUser.getMatricola() + "\n" +
                "- Password: " + kairosUser.getPassword() + "\n" +
                "Procedura di prenotazione automatica: " + (kairosUser.isAutoBooking() ? "ON\n" : "OFF\n");
        final List<LessonToBook> lessonsToBook = lessonToBookRepository.findByChatId(chat.id());
        if (!lessonsToBook.isEmpty()) {
            userData += "Corsi abilitati per la prenotazione automatica:\n";
            for (LessonToBook lessonToBook : lessonsToBook) {
                userData += lessonToBook.getCourseName() + "\n";
            }
        }
        return userData + "\nQUESTE INFORMAZIONI SONO VISIBILI SOLO A TE";
    }

    /**
     * Method to remove one user
     *
     * @param chat The rapresentation of the chat with the user
     * @return The data of the user
     */
    @MessageRequest("/stop")
    public String autoPrenotaStopped(Chat chat) {
        logMessage("/stop", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isEmpty())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser))
            return "Non puoi usare un comando adesso!";
        if (kairosUser.isAutoBooking()) {
            kairosUser.setAutoBooking(false);
            userRepository.save(kairosUser);
            return "Sistema di prenotazione automatica arrestato";
        } else {
            return "Sistema di prenotazione automatica non attivo";
        }
    }

    /**
     * Method to remove one user
     *
     * @param chat The rapresentation of the chat with the user
     * @return The data of the user
     */
    @MessageRequest("/rimuovi_corsi")
    public BaseRequest autoBookingCoursesRemove(Chat chat) {
        logMessage("/rimuovi_corso", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (optionalUser.isEmpty())
            return new SendMessage(chat.id(), "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start");
        final KairosUser kairosUser = optionalUser.get();
        if (checkCommandRunning(kairosUser)) {
            return new SendMessage(chat.id(), "Non puoi usare un comando adesso!");
        }
        if (kairosUser.getMatricola() == null || kairosUser.getPassword() == null)
            return new SendMessage(chat.id(), "Non è stato effettuato il login.\n" +
                    "Inserire /matricola e /password");
        kairosUser.setRemovingAutoBooking(true);
        userRepository.save(kairosUser);
        final List<LessonToBook> lessonsToBook = lessonToBookRepository.findByChatId(chat.id());
        final List<String> courses = new LinkedList<>();
        lessonsToBook.forEach(e -> courses.add(e.getCourseName()));
        final ReplyKeyboardMarkup lessonsMenu = new ReplyKeyboardMarkup(new KeyboardButton("Lista Corsi"));
        courses.forEach(e -> lessonsMenu.addRow(e));
        lessonsMenu.addRow("FINE");
        return new SendMessage(chat.id(),
                "Seleziona i corsi da rimuovere dalla prenotazione automatica.\n"
                        + "Quando hai finito digita 'FINE' per arrestare il processo di selezione e confermare le tue scelte.")
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .disableNotification(true)
                .replyMarkup(lessonsMenu);
    }

    /**
     * Method to remove one user
     *
     * @param chat The representation of the chat with the user
     * @return The data of the user
     */
    @MessageRequest("/logout")
    public String logout(Chat chat) {
        logMessage("/logout", chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (!optionalUser.isPresent())
            return "Utente non loggato";
        else {
            if (checkCommandRunning(optionalUser.get()))
                return "Non puoi utilizzare un comando adesso!";
            userRepository.delete(optionalUser.get());
            return "Credenziali eliminate";
        }
    }

    /**
     * Method that allow user to send a report
     *
     * @param chat The representation of the chat with the user
     */
    @MessageRequest("/segnalazione")
    public String writingReport(Chat chat) {
        final Optional<KairosUser> optionalKairosUser = userRepository.findByChadId(chat.id());
        if (optionalKairosUser.isEmpty())
            return "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start";
        final KairosUser kairosUser = optionalKairosUser.get();
        kairosUser.setWritingReport(true);
        userRepository.save(kairosUser);
        return "Inviami adesso la tua segnalazione, altrimenti digita \"ANNULLA\" per annulare";
    }

    /**
     * Method that given the lesson title books a lesson
     *
     * @param message Lesson to book
     * @param chat    The rappresentation of the chat with the user
     * @return The outcome of the operation
     */
    @MessageRequest("{lesson:.*}")
    public BaseRequest messageManager(@BotPathVariable("lesson") String message, Chat chat) {
        logMessage(message, chat.id());
        final Optional<KairosUser> optionalUser = userRepository.findByChadId(chat.id());
        if (!optionalUser.isPresent())
            return new SendMessage(chat.id(), "Utente non registrato!\n" +
                    "Per favore reinizializza il bot con il comando /start");
        final KairosUser kairosUser = optionalUser.get();

        // Adding matricola
        if (kairosUser.isAddingMatricola()) {
            return addMatricola(message, chat, kairosUser);
        }

        // Adding password
        else if (kairosUser.isAddingPassword()) {
            return addPassword(message, chat, kairosUser);
        }

        // Setting autoBooking procedure
        else if (kairosUser.isAddingAutoBooking()) {
            return setAutoBookingProcedure(message, chat, kairosUser);
        }

        // Removing courses from auto booking
        else if (kairosUser.isRemovingAutoBooking()) {
            return removeAutoBookingCourses(message, chat, kairosUser);
        }

        // Writing message report
        else if (kairosUser.isWritingReport()) {
            return sendReport(message, chat, kairosUser);
        }

        // Choosing and book lesson
        else {
            return choosingAndBookLesson(message, chat, kairosUser);
        }
    }

    private BaseRequest loginError(Long chatId) {
        return new SendMessage(chatId, "Ops... Qualcosa è andato storto\n" +
                "Controlla le tue credenziali e riprova!");
    }

    private BaseRequest sendReport(String message, Chat chat, KairosUser kairosUser) {
        if (message.equals("ANNULLA")) {
            kairosUser.setWritingReport(false);
            userRepository.save(kairosUser);
            return new SendMessage(chat.id(), "Operazione annullata");
        }
        List<DevUser> devUsers = devUserRepository.findAll();
        devUsers.stream().filter(DevUser::getIsDevUser).forEach(u -> devBot.execute(new SendMessage(u.getChatId(),
                "Reported by user: " + chat.id() +"\n\n" + message)));
        kairosUser.setWritingReport(false);
        userRepository.save(kairosUser);
        return new SendMessage(chat.id(), "Grazie della tua segnalazione.\n" +
                "Gli sviluppatori si occuperanno di gestire il problema da te riscontrato.");
    }

    private BaseRequest addMatricola(String message, Chat chat, KairosUser kairosUser) {
        if (isMatricolaValid(message)) {
            kairosUser.setMatricola(message);
            kairosUser.setAddingMatricola(false);
            userRepository.save(kairosUser);
            log.info("Utente salvato {}", kairosUser);
            return new SendMessage(chat.id(), "Matricola " + message + " salvata");
        } else {
            return new SendMessage(chat.id(), "Matricola non valida");
        }
    }

    private BaseRequest addPassword(String message, Chat chat, KairosUser kairosUser) {
        kairosUser.setPassword(message);
        kairosUser.setAddingPassword(false);
        userRepository.save(kairosUser);
        return new SendMessage(chat.id(), "Password cifrata e salvata con successo");
    }

    private BaseRequest setAutoBookingProcedure(String message, Chat chat, KairosUser kairosUser) {
        if (message.equals("FINE")) {
            kairosUser.setAddingAutoBooking(false);
            userRepository.save(kairosUser);
            return new SendMessage(chat.id(), "Comando eseguito correttamente!\n" +
                    "Se non già attiva utilizza il comando /start per avviare la procedura di auto prenotazione.");
        }
        LessonToBook lessonToBook = LessonToBook
                .builder()
                .courseName(message)
                .chatId(chat.id())
                .build();
        if (lessonToBookRepository.findByChatId(chat.id()).contains(lessonToBook))
            return new SendMessage(chat.id(), "Corso già aggiunto");
        lessonToBookRepository.save(lessonToBook);
        return new SendMessage(chat.id(), "Corso " + message + " aggiunto correttamente!\n" +
                "Per terminare digita 'FINE', altrimenti scegli un altro corso.");
    }

    private BaseRequest removeAutoBookingCourses(String message, Chat chat, KairosUser kairosUser) {
        if (message.equals("FINE")) {
            kairosUser.setRemovingAutoBooking(false);
            userRepository.save(kairosUser);
            return new SendMessage(chat.id(), "Operazione completata!");
        }
        final List<LessonToBook> lessonsToBook = lessonToBookRepository.findByChatId(chat.id());
        final LessonToBook lesson = lessonsToBook.stream().filter(e -> e.getCourseName().equals(message)).findFirst().get();
        lessonToBookRepository.delete(lesson);
        return new SendMessage(chat.id(), "Corso " + message + " rimosso correttamente!\n" +
                "Per terminare digita 'FINE', altrimenti scegli un altro corso.");
    }

    private BaseRequest choosingAndBookLesson(String message, Chat chat, KairosUser kairosUser) {
        if (isLessonWrongFormat(message)) {
            return new SendMessage(chat.id(), "Comando non disponibile");
        }
        try {
            messenger.sendMessageTo(chat.id(), "Elaborazione...");
            final List<Lesson> courses = booker.book(kairosUser.getMatricola(), kairosUser.getPassword(), message);
            final ReplyKeyboardMarkup lessonsMenu = new ReplyKeyboardMarkup(new KeyboardButton("Lista Corsi"));
            courses.forEach(e -> lessonsMenu.addRow(e.getCourseName() + " - " + e.getDate() + " " + (e.isBooked() ? "[🟢]" : "[🔴]")));
            final Lesson lesson = courses.stream().filter(e -> (e.getCourseName() + " - " + e.getDate() + " " + (!e.isBooked() ? "[🟢]" : "[🔴]")).equals(message)).findFirst().get();
            final SendMessage request = new SendMessage(kairosUser.getChadId(), lesson.isBooked() ? "Lezione prenotata" : "Prenotazione annullata")
                    .parseMode(ParseMode.HTML)
                    .disableWebPagePreview(true)
                    .disableNotification(true)
                    .replyMarkup(lessonsMenu);
            return request;
        } catch (Exception e) {
            return loginError(chat.id());
        }
    }

    private String logMessage(String message, Long chatId) {
        chatHistoryRepository.save(ChatHistory.builder()
                .chadId(chatId)
                .message(message)
                .timestamp(new Timestamp(new Date().getTime()))
                .build());
        log.info("Saved message: {}", message);
        return message;
    }

    boolean isLessonWrongFormat(String lesson) {
        return !Pattern.matches("([A-Z]* )*- .*", lesson);
    }

    private boolean isMatricolaValid(String matricola) {
        try {
            Long.parseLong(matricola);
        } catch (NumberFormatException ex) {
            return false;
        }
        return matricola.length() == 7;
    }

    private boolean checkCommandRunning(KairosUser kairosUser) {
        return kairosUser.isRemovingAutoBooking() || kairosUser.isAddingAutoBooking() || kairosUser.isAddingMatricola() || kairosUser.isAddingPassword() || kairosUser.isWritingReport();
    }

    @Scheduled(fixedDelay = 3600000)
    private void autoBooking() {
        log.info("Started auto booking");
        userRepository.findAll().forEach(u -> {
            if (u.isAutoBooking()) {
                final List<LessonToBook> lessonsToBook = lessonToBookRepository.findByChatId(u.getChadId());
                final List<String> lessonsName = new LinkedList<>();
                lessonsToBook.stream().map(LessonToBook::getCourseName).forEach(lessonsName::add);
                final int lessonsBooked = booker.autoBook(u.getMatricola(), u.getPassword(), lessonsName);
                if (lessonsBooked > 0) messenger.sendMessageTo(u.getChadId(), "Ho prenotato " + lessonsBooked + " lezioni!");
            }
        });
    }
}
