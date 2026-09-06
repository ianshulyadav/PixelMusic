package com.unshoo.pixelmusic.presentation.utils

import com.unshoo.pixelmusic.R

object GenreIconProvider {

    val DEFAULT_GENRES = listOf(
        "Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip Hop",
        "Country", "Blues", "Reggae", "Metal", "Folk", "R&B", "Punk", "Indie",
        "Alternative", "Latino", "Reggaeton", "Salsa", "Bachata", "Merengue", "Cumbia",
        "Oldies", "Soundtrack", "Gaming", "Sleep", "Workout", "Party", "Focus",
        "Gospel", "Children's", "World", "Dance", "New Age", "Easy Listening",
        "Afrobeats", "Synthwave", "Drum and Bass", "Lo-fi", "Phonk", "Anime",
        "Balada", "Sertanejo", "Forró", "Tango", "Norteño", "Música Tropical",
        "Schlager", "Chanson", "Enka", "Trot"
    )

    val SELECTABLE_ICONS = listOf(
        R.drawable.rock, R.drawable.pop_mic, R.drawable.sax, R.drawable.clasic_piano,
        R.drawable.electronic_sound, R.drawable.rapper, R.drawable.banjo, R.drawable.harmonica,
        R.drawable.maracas, R.drawable.metal_guitar, R.drawable.metal_guitar_2, R.drawable.accordion,
        R.drawable.synth_piano, R.drawable.punk, R.drawable.idk_indie_ig, R.drawable.acoustic_guitar,
        R.drawable.alt_video, R.drawable.star_angle, R.drawable.conga, R.drawable.bongos,
        R.drawable.drum, R.drawable.rattle, R.drawable.rounded_schedule_24, R.drawable.rounded_tv_24,
        R.drawable.rounded_touch_app_24, R.drawable.rounded_alarm_24, R.drawable.rounded_celebration_24,
        R.drawable.rounded_edit_24, R.drawable.rounded_favorite_24, R.drawable.rounded_lyrics_24,
        R.drawable.rounded_library_music_24, R.drawable.rounded_music_note_24,
        R.drawable.rounded_headphones_24, R.drawable.rounded_speaker_24
    )

    @Suppress("CyclomaticComplexMethod")
    fun getGenreImageResource(genreId: String, customIcons: Map<String, Int> = emptyMap()): Any {
        customIcons[genreId]?.let { return it }

        return when (genreId.lowercase().trim()) {

            "rock", "hard rock", "classic rock", "southern rock", "progressive rock",
            "prog rock", "progressive", "math rock", "post-rock", "post rock",
            "soft rock", "j-rock", "j rock", "art rock", "symphonic rock", "space rock",
            "psychedelic rock", "glam rock", "garage rock", "country rock", "slow rock",
            "post-grunge", "folk rock", "folk-rock", "swamp rock", "power pop rock",
            "rock and roll", "rock & roll",
            "rock/pop", "rock/metal", "rock/punk", "rock music",
            "rock clásico", "rock clasico", "rock duro", "rock progresivo",
            "rock suave", "rock alternativo", "rock en español", "rock en espanol",
            "rock brasileiro", "rock nacional",
            "rock français", "rock alternatif",
            "krautrock",
            "rock italiano",
            "ロック",
            "록",
            "摇滚", "摇滚乐", "搖滾", "搖滾樂", "民谣摇滚" -> R.drawable.rock

            "alternative", "alt-rock", "alternative rock", "experimental",
            "avant-garde", "avantgarde", "abstract", "psychedelic", "psychadelic",
            "neo-psychedelia", "art rock alt",
            "alternative/indie", "indie/alternative",
            "alternativo", "alternativa", "rock alternativo independiente",
            "alternativo brasileiro",
            "alternatif",
            "alternativ",
            "alternativa",
            "另类", "另類" -> R.drawable.alt_video

            "metal", "heavy metal", "death metal", "black metal", "thrash metal",
            "speed metal", "power metal", "doom metal", "stoner rock", "stoner metal",
            "sludge", "sludge metal", "gothic metal", "symphonic metal", "folk metal",
            "pagan metal", "viking metal", "glam metal",
            "metal music",
            "metal pesado", "metal extremo",
            "メタル", "ヘヴィメタル", "ヘビーメタル",
            "메탈", "헤비메탈",
            "金属", "重金属", "死亡金属", "金屬", "重金屬" -> R.drawable.metal_guitar

            "nu metal", "nu-metal", "metalcore", "deathcore", "screamo",
            "noise rock", "industrial", "noise", "grindcore", "djent",
            "mathcore", "technical death metal", "brutal death metal",
            "melodic death metal", "progressive death metal",
            "blackgaze", "ebm", "industrial rock", "post-metal",
            "terror", "frenchcore" -> R.drawable.metal_guitar_2

            "punk", "punk rock", "pop punk", "grunge", "emo", "post-punk",
            "post punk", "hardcore punk", "street punk", "skate punk",
            "melodic punk", "melodic hardcore", "acid punk", "folk punk",
            "garage punk", "anarcho-punk", "crust punk", "emo pop",
            "punk music",
            "punk/rock", "rock/punk",
            "punk en español",
            "パンク", "パンクロック",
            "펑크",
            "朋克" -> R.drawable.punk

            "indie", "indie rock", "indie pop", "lo-fi", "lo fi",
            "shoegaze", "dream pop", "noise pop", "twee pop", "sadcore", "slowcore",
            "britpop", "brit pop",
            "indie music",
            "indie en español", "indie latinoamericano",
            "インディー", "インディ",
            "인디", "인디 음악",
            "独立", "独立音乐", "獨立", "獨立音樂" -> R.drawable.idk_indie_ig

            "pop", "pop rock", "k-pop", "dance pop", "teen pop", "bubblegum pop",
            "adult contemporary", "j-pop", "c-pop", "mandopop", "cantopop",
            "dance-pop", "europop", "karaoke", "power pop", "art pop",
            "vocal", "top 40", "eurodance",
            "pop music", "pop/dance", "dance/pop", "pop/rock",
            "pop/latin", "latin/pop",
            "pop latino", "pop en español", "pop en espanol", "musica pop",
            "música pop", "pop español", "pop espanol", "contemporaneo", "contemporáneo",
            "mpb", "música popular brasileira", "musica popular brasileira",
            "brega", "arrocha", "tropicália", "tropicalia", "tropicalismo",
            "opm",
            "chanson", "chanson française", "chanson francaise",
            "variété", "variete", "variété française", "variete francaise",
            "schlager",
            "canzone italiana", "musica italiana", "cantautori", "cantautore",
            "musica napoletana", "napoletana",
            "t-pop", "v-pop",
            "ポップス", "ポップ", "Jポップ",
            "팝", "k-팝", "k팝", "트로트",
            "流行", "流行音乐", "流行音樂", "华语流行", "粤语", "粤語", "粤语流行",
            "粤语歌", "台语", "国语", "國語" -> R.drawable.pop_mic

            "synth-pop", "synthpop", "new wave", "electropop",
            "synthwave", "outrun", "retrowave", "vaporwave",
            "darkwave", "coldwave", "electroclash",
            "onda retro", "nueva ola",
            "neue deutsche welle", "ndw",
            "合成器流行" -> R.drawable.synth_piano

            "hip hop", "hip-hop", "rap", "trap", "gangsta rap", "reggaeton",
            "lo-fi hip hop", "lofi hip hop", "lo fi hip hop", "chillhop",
            "phonk", "drill", "cloud rap", "mumble rap", "trip-hop", "trip hop",
            "g-funk", "gangsta", "freestyle", "christian rap", "christian gangsta rap",
            "hip hop music", "hip-hop music", "rap music",
            "rap/hip-hop", "hip-hop/rap", "hip hop/rap", "trap/hip-hop",
            "hip-hop/trap",
            "hip hop en español", "rap en español", "rap en espanol",
            "rap español", "rap espanol", "trap en español", "trap en espanol",
            "corridos tumbados", "trap latino", "urbano",
            "funk carioca", "funk brasileiro", "brega funk",
            "rap français", "rap francais",
            "ヒップホップ", "ラップ",
            "힙합", "랩",
            "嘻哈", "说唱", "說唱" -> R.drawable.rapper

            "jazz", "smooth jazz", "bebop", "swing", "big band", "dixieland",
            "jazz fusion", "fusion", "cool jazz", "free jazz", "latin jazz",
            "acid jazz", "nu jazz", "spiritual jazz", "electro swing", "swing jazz",
            "fast fusion", "jazz+funk", "jazz blues",
            "jazz music",
            "jazz/blues", "blues/jazz",
            "choro", "chorinho",
            "ジャズ",
            "재즈",
            "爵士", "爵士乐", "爵士樂", "爵士蓝调" -> R.drawable.sax

            "blues", "rhythm & blues", "delta blues", "chicago blues",
            "electric blues", "boogie", "boogie-woogie",
            "blues music",
            "ブルース",
            "블루스",
            "蓝调", "藍調" -> R.drawable.harmonica

            "classical", "orchestra", "symphony", "piano", "baroque", "opera",
            "chamber", "chamber music", "choral", "contemporary classical",
            "neo-classical", "neoclassical", "minimalism", "string quartet",
            "piano classical", "romantic classical", "sonata", "chorus",
            "showtunes", "musical", "musicals", "broadway", "theatre music",
            "chamber pop", "baroque pop",
            "classical music",
            "clásica", "clasica", "música clásica", "musica clasica",
            "clásico", "clasico", "orquesta", "sinfonía", "sinfonia",
            "ópera", "barroco", "coro", "danzon", "danzón",
            "clássico", "classico", "música clássica", "musica classica", "clássica",
            "classique", "musique classique",
            "klassik", "klassische musik",
            "classica", "musica classica",
            "クラシック", "クラシック音楽", "クラシカル", "演歌",
            "클래식", "클래식 음악",
            "古典", "古典音乐", "古典音樂", "古典乐", "戏曲", "京剧", "昆曲" -> R.drawable.clasic_piano

            "electronic", "edm", "techno", "house", "trance", "dubstep", "electro",
            "deep house", "progressive house", "tropical house", "future bass",
            "ambient house", "garage", "uk garage", "disco", "euro-disco",
            "idm", "psytrance", "goa", "goa trance", "big beat", "rave",
            "euro-techno", "euro-house", "club-house", "techno-industrial",
            "electronic music", "dance/electronic", "electronic/dance",
            "electronic/dance", "dance/electronic",
            "electrónica", "electronica", "música electrónica", "musica electronica",
            "eletrônica", "eletronica", "música eletrônica", "musica eletronica",
            "baile funk", "tecnobrega",
            "électronique", "electronique", "musique électronique",
            "musique electronique", "électro",
            "elektronisch", "elektronische musik", "elektro",
            "elettronica", "musica elettronica",
            "エレクトロニック", "電子音楽", "エレクトロ",
            "일렉트로닉", "전자음악",
            "电子", "电子音乐", "電子", "電子音樂", "电音",
            "workout", "gym", "fitness", "running", "cardio", "sports",
            "workout music",
            "ejercicio", "entrenamiento", "gimnasio", "deporte", "deportes",
            "exercício", "exercicio", "academia", "treino",
            "exercice", "sport", "workout musik", "musik für sport" -> R.drawable.electronic_sound

            "drum and bass", "d&b", "dnb", "jungle", "breakbeat", "breaks" -> R.drawable.drum

            "hardstyle", "hardcore", "gabber" -> R.drawable.metal_guitar_2

            "sleep", "relax", "meditation", "ambient", "chillout", "chill out",
            "chill", "downtempo", "new age", "spa", "nature sounds",
            "dark ambient", "drone", "psybient",
            "sleep music", "meditation music", "ambient music", "new age music",
            "relajación", "relajacion", "meditación", "meditacion",
            "dormir", "música ambiental", "musica ambiental", "nueva era",
            "ambiente", "bienestar",
            "relaxamento", "relaxação", "relaxacao", "meditação", "meditacao",
            "méditation", "relaxation", "bien-être", "bien-etre",
            "entspannung", "schlafmusik", "naturgeräusche",
            "meditazione", "rilassamento", "musica rilassante",
            "睡眠", "リラックス", "瞑想",
            "명상", "힐링",
            "冥想", "放松", "睡眠", "新世纪", "新紀元", "禅", "佛教" -> R.drawable.rounded_alarm_24

            "country", "bluegrass", "americana", "ranchera", "corrido", "corridos",
            "country music",
            "country/folk", "folk/country",
            "regional mexicano", "regional mexicana", "música regional mexicana",
            "musica regional mexicana", "corridos del norte",
            "sertanejo", "sertanejo universitário", "sertanejo universitario",
            "sertanejo raiz",
            "カントリー",
            "컨트리",
            "乡村", "乡村音乐" -> R.drawable.banjo

            "folk", "acoustic", "singer-songwriter", "folk & acoustic",
            "nueva canción", "nueva cancion", "fado",
            "indie folk", "folk pop", "dark folk", "gothic folk", "anti-folk",
            "folk music", "acoustic music",
            "folk/acoustic", "acoustic/folk", "singer/songwriter",
            "folclore", "folklore", "música folclórica", "musica folklorica",
            "música folk", "musica folk", "trova", "nueva trova",
            "bambuco", "tonada",
            "mpb folk",
            "musique folk", "chanson folk",
            "volksmusik", "volkslied", "volkslieder",
            "folk italiano", "musica tradizionale", "folkloristica",
            "フォーク", "フォークソング",
            "포크",
            "民谣", "民謠", "民间音乐", "民間音樂", "民间歌曲" -> R.drawable.acoustic_guitar

            "r&b / soul", "rnb", "r&b", "soul", "funk", "motown",
            "neo-soul", "neo soul", "quiet storm", "slow jam", "ballad",
            "r&b music", "soul music", "funk music",
            "r&b/soul", "soul/r&b", "soul/funk", "funk/soul",
            "rhythm and blues", "balada", "balada romántica", "balada romantica",
            "romántica", "romantica",
            "ソウル", "ファンク",
            "소울", "발라드",
            "灵魂乐", "放克", "靈魂樂", "節奏藍調", "节奏布鲁斯" -> R.drawable.synth_piano

            "salsa", "samba", "mambo", "rumba", "cha-cha", "cha cha", "chacha",
            "son cubano", "son", "flamenco", "champeta", "cumbia villera",
            "guaracha", "timba", "landó", "lando", "festejo",
            "boogaloo", "son montuno", "salsa romántica", "salsa romantica",
            "salsa dura", "timba cubana", "mozambique", "rumba flamenca",
            "flamenco pop", "nuevo flamenco", "latin soul", "afrolatino",
            "duranguense", "cumbia sonidera", "mapalé", "mapale",
            "currulao", "garifuna",
            "afrobeat", "afrobeats", "afropop", "afro", "highlife",
            "soukous", "kizomba", "kuduro", "semba", "rebita", "kwaito",
            "amapiano", "gqom", "afro house", "afrohouse", "bongo flava",
            "juju", "makossa",
            "axé", "axe", "axé music" -> R.drawable.conga

            "bachata", "tango", "bolero", "zouk",
            "tango nuevo", "new tango", "tango argentino",
            "milonga", "zamba", "pasillo", "música criolla", "musica criolla",
            "milonga argentina", "pasillo colombiano", "zamba argentina" -> R.drawable.bongos

            "merengue", "banda", "merengue urbano",
            "pagode", "pagode baiano", "pagode baiana" -> R.drawable.drum

            "cumbia", "mariachi", "marimba", "huapango", "porro",
            "bossa nova", "bossanova", "bossa", "soca", "calypso",
            "chacarera", "cueca", "son jarocho", "joropo", "gaita",
            "música andina", "musica andina", "cumbia andina", "cumbia chilena",
            "punta", "música tropical", "musica tropical",
            "música llanera", "musica llanera", "quebradita",
            "huayno", "saya",
            "frevo", "carimbó", "carimbo", "lambada", "piseiro", "pisadinha",
            "forró", "forro", "xote", "xaxado", "maracatu",
            "tarantella",
            "samba-reggae",
            "ボサノバ", "レゲエ",
            "레게" -> R.drawable.maracas

            "norteño", "norteno", "tejano", "grupero",
            "polka", "klezmer", "musette",
            "cuarteto", "vallenato",
            "baião", "baiao", "música nordestina", "musica nordestina",
            "cajun", "zydeco", "celtic", "irish",
            "folk scandinavia", "nordic" -> R.drawable.accordion

            "latino", "latin", "latin pop", "urbano latino", "tropical",
            "latin alternative", "latin rock", "tropipop",
            "música latina", "musica latina", "pop latinoamericano",
            "música latinoamericana", "musica latinoamericana" -> R.drawable.star_angle

            "reggae", "ska", "dancehall", "roots reggae", "dub",
            "reggae music",
            "reggae/ska", "ska/reggae" -> R.drawable.maracas

            "world", "world music", "ethnic", "folk world & country",
            "traditional", "indigenous", "tribal", "global",
            "bollywood", "filmi", "bhangra", "carnatic", "hindustani",
            "ghazal", "qawwali", "rai", "chaabi", "arabic pop", "arab pop",
            "turkish pop",
            "música del mundo", "musica del mundo", "música mundial",
            "musica mundial", "étnica", "etnica", "tradicional", "indígena", "indigena",
            "música tradicional", "musica tradicional",
            "música do mundo",
            "musique du monde", "musique africaine", "musique traditionnelle",
            "weltmusik",
            "musica del mondo", "musica tradizionale italiana",
            "民謡", "日本民謡",
            "국악", "민요", "한국 민요",
            "民族", "传统", "中国传统音乐", "國風", "国风", "中国风", "古风",
            "傳統", "民族音乐" -> R.drawable.rattle

            "gospel", "christian", "christian rock", "ccm", "contemporary christian",
            "spiritual", "religious", "worship", "praise",
            "gospel/christian", "christian/gospel",
            "música cristiana", "musica cristiana", "cristiana", "evangélica",
            "evangelica", "música gospel", "musica gospel", "música espiritual",
            "musica espiritual", "alabanza", "adoración", "adoracion",
            "música cristã", "musica crista", "cristã", "crista", "louvores",
            "évangile", "evangile", "musique chrétienne", "musique chretienne",
            "louanges", "cantiques",
            "kirchenmusik",
            "gospel italiano", "musica cristiana italiana",
            "福音", "基督教音乐", "圣歌", "赞美诗", "讚美詩" -> R.drawable.rounded_favorite_24

            "children's", "children", "kids", "nursery", "nursery rhymes",
            "baby", "lullaby", "lullabies",
            "kids music", "children music", "children's music",
            "música infantil", "musica infantil", "infantil", "niños", "ninos",
            "canciones infantiles", "rondas", "nanas", "para niños", "para ninos",
            "canções infantis", "cancoes infantis",
            "musique pour enfants", "enfants", "comptines",
            "kindermusik", "kinder", "kinderlieder", "kinderlied",
            "musica per bambini", "bambini", "filastrocche", "canzoni per bambini",
            "子供", "こども", "童謡", "子供の歌",
            "어린이", "동요",
            "儿歌", "童謠", "童谣", "兒歌", "儿童" -> R.drawable.rattle

            "spoken word", "poetry", "audiobook", "spoken",
            "podcast", "speech", "audio theatre", "audio theater",
            "comedy/spoken",
            "palabra hablada", "poesía", "poesia", "audiolibro",
            "朗読", "詩",
            "有声书", "朗诵" -> R.drawable.rounded_lyrics_24

            "comedy", "humor", "humour", "satire", "pranks",
            "comedia", "humor musical",
            "comédie", "comedie",
            "commedia" -> R.drawable.rounded_celebration_24

            "christmas", "holiday", "festive", "seasonal",
            "christmas music", "holiday music",
            "navidad", "navideña", "navidena", "música navideña",
            "musica navidena", "villancicos", "aguinaldos", "posadas",
            "natal", "músicas natalinas", "musicas natalinas", "música de natal",
            "musica de natal",
            "noël", "noel", "musique de noël", "musique de noel",
            "weihnachtsmusik", "weihnachten", "weihnachtslieder",
            "natale", "musica natalizia", "canzoni di natale",
            "クリスマス", "クリスマスソング",
            "크리스마스",
            "圣诞", "聖誕", "圣诞节", "圣诞歌曲" -> R.drawable.rounded_celebration_24

            "oldies", "retro", "80s", "90s", "70s", "60s", "50s",
            "classic hits", "throwback", "revival",
            "viejitos", "clásicos", "clasicos", "viejos éxitos",
            "viejos exitos", "nostalgia",
            "clássicos", "classicos",
            "rétro", "années 80", "années 90",
            "oldies deutsch",
            "懐かしの曲", "昭和",
            "옛날노래",
            "怀旧", "懷舊" -> R.drawable.rounded_schedule_24

            "soundtrack", "score", "film score", "movie tunes", "ost",
            "anime soundtrack", "anime", "trailer", "trailer music",
            "k-drama ost", "k-drama",
            "banda sonora", "música de película", "musica de pelicula",
            "música de cine", "musica de cine", "música original", "musica original",
            "trilha sonora", "trilha",
            "bande originale", "bande-son", "bande son", "musique de film",
            "filmmusik",
            "colonna sonora",
            "サウンドトラック", "映画音楽", "アニメ", "アニメソング", "アニソン",
            "사운드트랙", "영화음악", "애니메이션",
            "原声", "电影原声", "影视原声", "动漫", "動漫" -> R.drawable.rounded_tv_24

            "gaming", "video game music", "chiptune", "8-bit", "game music",
            "video game", "video games", "vgm", "game",
            "game soundtrack", "game ost", "video game ost",
            "video game soundtrack", "retro gaming", "arcade", "console music",
            "8-bit music", "chiptune music", "gaming music",
            "música de videojuegos", "musica de videojuegos", "videojuegos",
            "música de jogos", "musica de jogos",
            "ゲーム音楽", "ゲームミュージック", "BGM",
            "게임 음악", "게임음악",
            "游戏音乐", "遊戲音樂" -> R.drawable.rounded_touch_app_24

            "party", "club", "dance", "dance music",
            "fiesta", "baile", "música de baile", "musica de baile", "discoteca",
            "festa", "dança", "danca",
            "fête", "fete", "danse", "soirée", "soiree",
            "tanz", "tanzmusik",
            "discoteca", "ballo", "musica dance",
            "ダンス", "ダンスミュージック",
            "댄스", "댄스 음악",
            "舞曲", "舞蹈", "派对" -> R.drawable.rounded_celebration_24

            "focus", "study", "concentration", "study music", "focus music",
            "concentración", "concentracion", "estudio", "música de estudio",
            "musica de estudio",
            "concentração", "concentracao",
            "concentration", "étude", "etude",
            "konzentration", "lernen",
            "concentrazione", "studio",
            "集中", "勉強",
            "공부", "집중",
            "专注", "學習", "学习", "專注" -> R.drawable.rounded_edit_24

            "easy listening", "lounge", "background music", "smooth",
            "easy", "lounge music",
            "música suave", "musica suave", "música de fondo", "musica de fondo",
            "música de ambiente",
            "musique d'ambiance",
            "hintergrundmusik",
            "musica di sottofondo",
            "イージーリスニング",
            "이지 리스닝" -> R.drawable.rounded_headphones_24

            "instrumental", "acapella", "a cappella", "a capella",
            "instrumental music",
            "musique instrumentale",
            "instrumentalmusik",
            "musica strumentale",
            "インストゥルメンタル", "インスト",
            "器乐", "純音樂", "纯音乐", "轻音乐" -> R.drawable.rounded_music_note_24

            "ringtone", "ringtones", "notification", "notification sound",
            "notification tone", "alert", "alert tone", "phone tone",
            "message tone", "alarm tone", "tone", "tones",
            "tono", "tonos", "tono de llamada", "tonos de llamada",
            "toque", "toque de celular", "toque de chamada" -> R.drawable.rounded_music_note_24

            "favorites", "favourites", "favorite", "favourite",
            "greatest hits", "best of", "hits", "best hits", "top hits",
            "popular", "trending", "chart", "charts",
            "favoritos", "favoritas", "éxitos", "exitos",
            "lo mejor de", "los mejores éxitos", "los mejores exitos",
            "más popular", "mas popular",
            "sucessos", "melhores músicas", "melhores musicas",
            "favoris", "meilleures chansons", "tubes",
            "favoriten", "beste lieder",
            "preferiti", "successi",
            "最爱", "精選", "精选", "热门" -> R.drawable.rounded_favorite_24

            "remix", "remixes", "remix ep", "dj mix", "dj set", "dj",
            "continuous mix", "mashup", "mash-up", "mash up",
            "rework", "reworks", "edits", "bootleg remix",
            "extended mix", "radio edit", "club mix",
            "flip", "refix", "re-edit", "vip" -> R.drawable.electronic_sound

            "live", "live music", "concert", "live concert", "live session",
            "live at", "mtv unplugged", "live recording", "live performance",
            "live album",
            "en vivo", "concierto", "en directo", "directo",
            "ao vivo", "concerto",
            "en direct", "concert live",
            "live konzert", "konzert",
            "dal vivo", "concerto live",
            "ライブ", "コンサート",
            "라이브" -> R.drawable.rounded_music_note_24

            "unplugged", "cover", "covers", "cover song", "cover songs",
            "tribute", "acoustic cover", "acoustic session",
            "b-sides", "b sides", "rarities", "demo", "demos",
            "bootleg", "outtake", "outtakes",
            "versión acústica", "version acustica", "versiones",
            "acústico", "acustico", "versões" -> R.drawable.acoustic_guitar

            "happy", "upbeat", "energetic", "feel good", "feelgood",
            "fun", "euphoric", "cheerful", "joyful", "positive", "hype",
            "alegre", "animado", "animada", "feliz", "divertido", "divertida",
            "animado", "animada",
            "joyeux", "joyeuse", "gai",
            "fröhlich",
            "allegro", "gioioso" -> R.drawable.rounded_celebration_24

            "romantic", "romance", "love", "love songs", "love music",
            "sensual", "passionate", "intimate",
            "amor", "canciones de amor", "romantico", "romántico",
            "amor", "romântico", "romântica",
            "romantique", "amour",
            "romantisch", "liebeslieder",
            "romantico", "romantica", "amore",
            "情歌", "爱情", "愛情" -> R.drawable.rounded_favorite_24

            "sad", "melancholic", "melancholy", "emotional", "heartbreak",
            "breakup", "lonely", "tearjerker", "bittersweet", "somber",
            "triste", "tristeza", "melancolico", "melancólico", "desamor",
            "triste", "tristeza", "melancólico",
            "triste", "mélancolique",
            "traurig", "melancholisch",
            "malinconico",
            "悲伤", "忧郁" -> R.drawable.synth_piano

            "peaceful", "calm", "soothing", "tranquil", "serene",
            "gentle", "quiet", "mellow", "soft",
            "tranquilo", "tranquila", "calmado", "calmada",
            "tranquilo", "calmo",
            "calme", "paisible", "doux",
            "ruhig", "sanft",
            "tranquillo", "calmo",
            "轻柔", "安静" -> R.drawable.rounded_headphones_24

            "driving", "road trip", "travel", "commute", "car music",
            "highway", "cruising",
            "manejar", "conducir", "viaje", "carretera", "música para manejar",
            "dirigindo", "viagem",
            "conduite", "voyage",
            "autofahren", "reise" -> R.drawable.electronic_sound

            "morning", "wake up", "wakeup", "good morning", "sunrise",
            "breakfast", "morning routine",
            "mañana", "despertar", "buenos días", "buenos dias",
            "manhã", "manha", "acordar",
            "matin", "réveil",
            "morgen", "aufwachen",
            "mattina", "risveglio",
            "朝", "目覚め" -> R.drawable.rounded_alarm_24

            "night", "late night", "nighttime", "midnight", "evening",
            "after hours", "nocturnal",
            "noche", "tarde", "medianoche",
            "noite",
            "nuit",
            "nacht",
            "notte",
            "夜晚", "夜曲" -> R.drawable.rounded_alarm_24

            "work", "office", "work music", "office music",
            "trabajo", "oficina",
            "trabalho",
            "travail", "bureau",
            "arbeit", "büro",
            "lavoro", "ufficio" -> R.drawable.rounded_edit_24

            "wedding", "wedding music", "marriage", "matrimony", "graduation",
            "ceremony", "formal",
            "boda", "matrimonio", "graduación", "graduacion",
            "quinceañera", "quinceanera", "quince años", "quince anos",
            "casamento", "formatura",
            "mariage",
            "hochzeit", "hochzeitsmusik",
            "matrimonio", "nozze",
            "婚礼", "毕业" -> R.drawable.clasic_piano

            "birthday", "birthday music", "happy birthday",
            "cumpleaños", "feliz cumpleaños", "feliz cumpleanos",
            "aniversário", "aniversario", "parabéns", "parabens",
            "anniversaire", "joyeux anniversaire",
            "geburtstag", "zum geburtstag",
            "compleanno", "buon compleanno",
            "生日", "生日歌" -> R.drawable.rounded_celebration_24

            "2000s", "00s", "2010s", "10s", "2020s", "20s",
            "millennium", "vintage", "classic",
            "años 2000", "los 2000", "años 2010",
            "复古" -> R.drawable.rounded_schedule_24

            "hifi", "hi-fi", "hi fi", "lossless", "flac", "audiophile",
            "high fidelity", "high quality", "hd audio", "hi-res",
            "binaural", "asmr", "dolby", "surround",
            "alta fidelidad", "alta calidad",
            "alta fidelidade" -> R.drawable.rounded_headphones_24

            "sound effects", "sfx", "fx", "nature", "rain", "ocean", "waves",
            "birds", "birdsong", "white noise", "pink noise", "brown noise",
            "thunder", "storm", "wind", "waterfall", "forest", "fire",
            "frequency", "binaural beats", "432hz", "528hz", "174hz",
            "efectos de sonido", "sonidos de la naturaleza", "lluvia",
            "efeitos sonoros", "sons da natureza", "chuva",
            "regengeräusche",
            "自然音", "雨音",
            "自然声音", "雨声" -> R.drawable.rounded_alarm_24

            "music", "audio", "sound", "track", "song", "songs",
            "various", "various artists", "va", "compilation", "compil",
            "mix", "mixtape", "playlist", "collection", "medley",
            "new", "new music", "latest", "recent", "other", "misc",
            "miscellaneous", "general", "uncategorized",
            "música", "musica", "canción", "cancion", "canciones",
            "varios", "variado", "varios artistas", "recopilatorio",
            "colección", "coleccion",
            "canção", "cancao", "canções", "cancoes",
            "vários", "coletânea", "coletanea",
            "musique", "compilation fr",
            "musik", "sammlung",
            "musica generica", "raccolta",
            "音楽", "曲", "楽曲",
            "음악", "노래",
            "音乐", "歌曲", "音樂" -> R.drawable.rounded_library_music_24

            "unknown" -> R.drawable.rounded_question_mark_24
            else -> R.drawable.rounded_library_music_24
        }
    }
}
